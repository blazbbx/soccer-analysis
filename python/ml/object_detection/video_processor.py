import logging
import time

import numpy as np
import supervision as sv

from config import config
from object_detection.detector import ObjectDetector
from object_detection.event_detector import EventDetector
from object_detection.minimap_calculator import MinimapCalculator
from object_detection.team_classifier import TeamClassifier
from util.defisheyer import Defisheyer

logger = logging.getLogger(__name__)


# Output JSON contract (consumed by the React frontend via Match.trackingDataUrl):
#
# {
#   "videoFps": int,
#   "trackingData": [{"frame", "player_id", "team", "x1","y1","x2","y2", "tx","ty"}],
#   "ballData":     [{"frame", "x1","y1","x2","y2", "tx","ty"}],
#   "labelData":    [{"goal": [...], "corner": [...], "freekick": [...]}]
# }


class VideoFrameProcessor:
    """Per-match processor. Construct fresh for every RabbitMQ message, since
    the team colors, field corners, and per-match tracker state are all
    message-scoped. The expensive `ObjectDetector` is constructed once at
    worker boot and injected.
    """

    def __init__(
        self,
        detector: ObjectDetector,
        corners_xy: list[tuple[int, int]],
        home_hex: str,
        away_hex: str,
        referee_hex: str,
    ):
        self.detector = detector
        self.corners_xy = corners_xy
        self.team_classifier = TeamClassifier(home_hex, away_hex, referee_hex)

        self._polygon = np.array(corners_xy, dtype=np.int32)
        self._player_zone = sv.PolygonZone(
            polygon=self._polygon,
            triggering_anchors=(sv.Position.BOTTOM_CENTER,),
        )

        # Separate trackers — ball detection is much sparser, so it needs a
        # longer lost-track buffer than the players.
        self._player_tracker = sv.ByteTrack(
            lost_track_buffer=config.PLAYER_TRACK_BUFFER,
        )
        self._ball_tracker = sv.ByteTrack(
            lost_track_buffer=config.BALL_TRACK_BUFFER,
            track_activation_threshold=config.BALL_CONF,
        )

        # The minimap calculator is constructed per video — we need to know the
        # frame resolution before we can defish, and the four corner coordinates
        # are camera-specific.
        self.minimap = MinimapCalculator(
            source=np.array(corners_xy),
            minimap_size=(config.MINIMAP_WIDTH, config.MINIMAP_HEIGHT),
        )
        self.event_detector = EventDetector(
            config.MINIMAP_WIDTH, config.MINIMAP_HEIGHT,
        )

    # How often to emit a progress line during the per-frame loop. Tuned so a
    # short clip still gets a handful of updates and a long match doesn't spam.
    _PROGRESS_LOG_EVERY = 30
    # Ball detections are sparse — also log the first time we see one, then
    # every Nth subsequent hit to confirm we're still detecting it.
    _BALL_LOG_EVERY = 30

    def process(self, local_video_path: str) -> dict:
        video_info = sv.VideoInfo.from_video_path(local_video_path)
        total_frames = video_info.total_frames or 0
        logger.info(
            f"[*] Video info: {video_info.width}x{video_info.height} "
            f"@ {video_info.fps}fps, {total_frames} frames",
        )

        logger.info("[*] Initializing defish maps...")
        defisheyer = Defisheyer(
            coefficients=config.DEFISH_COEFFS,
            zoom_factor=config.DEFISH_ZOOM,
        )
        defisheyer.initialize_maps(video_info.width, video_info.height)
        logger.info("[*] Defish maps ready. Starting per-frame inference loop...")

        # Bboxes are detected on the native-resolution defished frame, but the
        # frontend renders them on the HLS-encoded video (1280x720 by default).
        # Scale so `entry.x1 * canvas.width / video.videoWidth` lines up.
        bbox_scale_x = config.HLS_OUTPUT_WIDTH / video_info.width
        bbox_scale_y = config.HLS_OUTPUT_HEIGHT / video_info.height
        logger.info(
            f"[*] Bbox scale: defished {video_info.width}x{video_info.height} "
            f"-> HLS {config.HLS_OUTPUT_WIDTH}x{config.HLS_OUTPUT_HEIGHT} "
            f"(x={bbox_scale_x:.3f}, y={bbox_scale_y:.3f})",
        )

        tracking_data: list[dict] = []
        ball_data: list[dict] = []
        frames = sv.get_video_frames_generator(source_path=local_video_path)

        start_time = time.time()
        ball_hits = 0
        last_frame_idx = -1

        for frame_idx, raw_frame in enumerate(frames):
            defished = defisheyer.undistort(raw_frame)

            players, balls = self.detector.detect(defished)

            if frame_idx == 0:
                logger.info(
                    f"[*] First frame inferred in "
                    f"{time.time() - start_time:.1f}s "
                    f"(includes one-time model warm-up). "
                    f"raw player_detections={len(players)}, "
                    f"raw ball_detections={len(balls)}",
                )

            # Players: clip to the user-confirmed field polygon, then track.
            inside = self._player_zone.trigger(players)
            players = players[inside]
            players = self._player_tracker.update_with_detections(players)

            # Ball: no spatial filter — it can leave the field. Track separately.
            balls = self._ball_tracker.update_with_detections(balls)

            # Use the first surviving ball detection as "the" ball this frame.
            ball_xy_minimap: tuple[float, float] | None = None
            if len(balls) > 0:
                bx1, by1, bx2, by2 = balls.xyxy[0]
                ball_center = np.array([[(bx1 + bx2) / 2.0, (by1 + by2) / 2.0]])
                ball_minimap = self.minimap.transform_points(ball_center)[0]
                ball_xy_minimap = (float(ball_minimap[0]), float(ball_minimap[1]))

                ball_hits += 1
                if ball_hits == 1:
                    logger.info(
                        f"[*] First ball detection at frame {frame_idx} "
                        f"(minimap=({ball_xy_minimap[0]:.1f},{ball_xy_minimap[1]:.1f}))",
                    )
                elif ball_hits % self._BALL_LOG_EVERY == 0:
                    logger.info(
                        f"[*] Ball hit #{ball_hits} at frame {frame_idx}",
                    )

            self.event_detector.push(frame_idx, ball_xy_minimap)

            # Only persist rows on emission frames; inference still runs every
            # frame so ball recall isn't degraded.
            if frame_idx % config.EMISSION_INTERVAL == 0:
                if len(players) > 0:
                    tracking_data.extend(
                        self._build_player_rows(
                            defished, players, frame_idx, bbox_scale_x, bbox_scale_y,
                        ),
                    )
                if len(balls) > 0:
                    ball_data.append(
                        self._build_ball_row(
                            balls, frame_idx, bbox_scale_x, bbox_scale_y,
                        ),
                    )

            if (frame_idx + 1) % self._PROGRESS_LOG_EVERY == 0:
                elapsed = time.time() - start_time
                fps = (frame_idx + 1) / elapsed if elapsed > 0 else 0.0
                pct = ((frame_idx + 1) / total_frames * 100.0) if total_frames else 0.0
                eta = (
                    (total_frames - (frame_idx + 1)) / fps
                    if fps > 0 and total_frames
                    else 0.0
                )
                logger.info(
                    f"[*] Frame {frame_idx + 1}/{total_frames or '?'} "
                    f"({pct:.1f}%) — {fps:.2f} fps, ETA {eta:.0f}s, "
                    f"player_rows={len(tracking_data)}, ball_rows={len(ball_data)}, "
                    f"ball_hits={ball_hits}",
                )
            last_frame_idx = frame_idx

        elapsed = time.time() - start_time
        events = self.event_detector.snapshot()
        logger.info(
            f"[*] Per-frame loop finished. {last_frame_idx + 1} frames in "
            f"{elapsed:.1f}s ({(last_frame_idx + 1) / elapsed:.2f} fps avg). "
            f"player_rows={len(tracking_data)}, ball_rows={len(ball_data)}, "
            f"ball_hits={ball_hits}, events={events}",
        )
        return {
            "videoFps": int(video_info.fps),
            "trackingData": tracking_data,
            "ballData": ball_data,
            "labelData": [events],
        }

    def _build_player_rows(
        self,
        frame_bgr: np.ndarray,
        players: sv.Detections,
        frame_idx: int,
        scale_x: float,
        scale_y: float,
    ) -> list[dict]:
        labels = self.team_classifier.classify(frame_bgr, players.xyxy)

        # Feet are computed in native defished space (for the perspective
        # transform), since the minimap calibration was done in that space.
        feet = np.stack(
            [
                (players.xyxy[:, 0] + players.xyxy[:, 2]) / 2.0,
                players.xyxy[:, 3],
            ],
            axis=1,
        )
        minimap_xy = self.minimap.transform_points(feet)

        rows: list[dict] = []
        for i, (x1, y1, x2, y2) in enumerate(players.xyxy):
            tracker_id = (
                int(players.tracker_id[i]) if players.tracker_id is not None else -1
            )
            tx, ty = float(minimap_xy[i, 0]), float(minimap_xy[i, 1])
            rows.append(
                {
                    "frame": frame_idx,
                    "player_id": tracker_id,
                    "team": labels[i],
                    "x1": round(float(x1) * scale_x, 1),
                    "y1": round(float(y1) * scale_y, 1),
                    "x2": round(float(x2) * scale_x, 1),
                    "y2": round(float(y2) * scale_y, 1),
                    "tx": round(tx, 2),
                    "ty": round(ty, 2),
                },
            )
        return rows

    def _build_ball_row(
        self,
        balls: sv.Detections,
        frame_idx: int,
        scale_x: float,
        scale_y: float,
    ) -> dict:
        x1, y1, x2, y2 = balls.xyxy[0]
        ball_center = np.array([[(x1 + x2) / 2.0, (y1 + y2) / 2.0]])
        tx, ty = self.minimap.transform_points(ball_center)[0]
        return {
            "frame": frame_idx,
            "x1": round(float(x1) * scale_x, 1),
            "y1": round(float(y1) * scale_y, 1),
            "x2": round(float(x2) * scale_x, 1),
            "y2": round(float(y2) * scale_y, 1),
            "tx": round(float(tx), 2),
            "ty": round(float(ty), 2),
        }
