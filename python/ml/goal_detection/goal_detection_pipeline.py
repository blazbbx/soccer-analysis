import json
import logging
import subprocess

import ffmpeg
import numpy as np

from config import config
from goal_detection.camera_stability import CameraStabilityDetector
from goal_detection.goal_pose_predictor import Corner, GoalPosePredictor
from goal_detection.temporal_median import compute_median_image, sample_stride
from util.defisheyer import Defisheyer
from util.minio_client import MinioClient
from util.rabbitmq_client import RabbitMQClient

logger = logging.getLogger(__name__)

INPUT_QUEUE = "goal-detection-queue"
OUTPUT_EXCHANGE = "detection-exchange"
OUTPUT_ROUTING_KEY = "goal.detected"


class GoalDetectionPipeline:
    def __init__(self, minio_client: MinioClient, rmq_client: RabbitMQClient):
        self.minio = minio_client
        self.rmq = rmq_client
        self.pose_predictor = GoalPosePredictor()

    def handle_message(self, ch, method, properties, body):
        match_id = "UNKNOWN"
        try:
            message = json.loads(body)
            video_url = message["videoUrl"]
            match_id = message["matchId"]

            logger.info(f"[➡] Goal detection request for matchId={match_id}")

            median_frame = self._build_median_background(video_url, match_id)
            defished = self._defish(median_frame)

            raw_goals = self.pose_predictor.predict_goals(defished)
            goals = [self._classify_corners(corners, defished.shape) for corners in raw_goals]

            self.rmq.publish(
                exchange=OUTPUT_EXCHANGE,
                routing_key=OUTPUT_ROUTING_KEY,
                message={
                    "matchId": match_id,
                    "status": "SUCCESS",
                    "goals": goals,
                },
            )
            logger.info(f"[⬅] Sent goal.detected for matchId={match_id}")

        except Exception as e:
            logger.exception(f"[!] Goal detection failed for matchId={match_id}")
            self.rmq.publish(
                exchange=OUTPUT_EXCHANGE,
                routing_key=OUTPUT_ROUTING_KEY,
                message={
                    "matchId": match_id,
                    "status": "ERROR",
                    "errorMessage": str(e),
                },
            )
        finally:
            ch.basic_ack(delivery_tag=method.delivery_tag)

    def _build_median_background(self, video_url: str, match_id: str) -> np.ndarray:
        """Jumps to several points spread across the match
        (`GOAL_DETECTION_JUMP_FRACTIONS`) instead of reading one continuous
        window from near the start. Each jump is a cheap, keyframe-based
        ffmpeg seek (the same trick field_detection_pipeline.py already uses
        for its single-frame extraction) — never a linear decode of
        everything before it, so this still never downloads/decodes the
        full match (docs/ai-collaboration-log.md, 2026-09-26, 3. döntés).

        Spreading across widely-separated points (rather than one 10s
        window) means one unlucky static moment — a kickoff lineup, a
        mid-match camera adjustment — only spoils *one* jump's worth of
        samples, not the whole median (docs/ai-collaboration-log.md,
        2026-09-28). Jump points that never settle within
        `GOAL_DETECTION_JUMP_STABILITY_TIMEOUT_SECONDS` are skipped
        entirely rather than contributing unstable frames.

        If every single jump point fails to stabilize, this raises rather
        than guessing — silently falling back to an unstable frame would
        produce a median that looks fine but is quietly wrong, which is
        worse than a visible failure.
        """
        width, height, fps, duration = self._probe_video(video_url)
        frame_size = width * height * 3

        fractions = config.GOAL_DETECTION_JUMP_FRACTIONS
        samples_per_jump = max(config.GOAL_DETECTION_SAMPLE_COUNT // len(fractions), 1)

        sampled_frames: list[np.ndarray] = []
        for fraction in fractions:
            seek_seconds = duration * fraction
            burst = self._grab_stable_burst(
                video_url, seek_seconds, width, height, frame_size, fps,
                samples_per_jump, match_id,
            )
            sampled_frames.extend(burst)

        if not sampled_frames:
            msg = (
                f"Camera was never stable at any of the {len(fractions)} sample "
                f"points for matchId={match_id}"
            )
            raise RuntimeError(msg)

        return compute_median_image(sampled_frames)

    def _grab_stable_burst(
        self,
        video_url: str,
        seek_seconds: float,
        width: int,
        height: int,
        frame_size: int,
        fps: float,
        samples_needed: int,
        match_id: str,
    ) -> list[np.ndarray]:
        """Seeks to `seek_seconds`, waits (up to a timeout) for LOCAL
        stability there, then collects `samples_needed` frames spread
        across a short local window. Returns an empty list (never raises)
        if this particular point never settles — the caller just gets
        fewer total samples, from the other jump points.
        """
        max_wait_frames = int(fps * config.GOAL_DETECTION_JUMP_STABILITY_TIMEOUT_SECONDS)
        burst_window_frames = max(
            int(fps * config.GOAL_DETECTION_BURST_WINDOW_SECONDS), samples_needed,
        )
        read_cap_seconds = (
            config.GOAL_DETECTION_JUMP_STABILITY_TIMEOUT_SECONDS
            + config.GOAL_DETECTION_BURST_WINDOW_SECONDS
            + 1  # margin so a slow-to-stabilize burst isn't cut off mid-sample
        )

        process = (
            ffmpeg
            .input(video_url, ss=seek_seconds)
            .output("pipe:", t=read_cap_seconds, format="rawvideo", pix_fmt="bgr24")
            .run_async(pipe_stdout=True)
        )

        stability = CameraStabilityDetector(
            motion_threshold=config.GOAL_STABILITY_MOTION_THRESHOLD,
            required_stable_frames=config.GOAL_STABILITY_REQUIRED_FRAMES,
        )

        prev_frame: np.ndarray | None = None
        collected: list[np.ndarray] = []
        stable_at: int | None = None
        stride = 1
        frame_index = 0

        try:
            while True:
                raw = process.stdout.read(frame_size)
                if len(raw) < frame_size:
                    break
                # .copy() -- np.frombuffer is a read-only view into a buffer
                # we're about to overwrite on the next read.
                frame = np.frombuffer(raw, dtype=np.uint8).reshape((height, width, 3)).copy()

                if stable_at is None:
                    if stability.feed(frame, prev_frame):
                        stable_at = frame_index
                        stride = sample_stride(burst_window_frames, samples_needed)
                    elif frame_index >= max_wait_frames:
                        break  # gave up on this jump point
                    prev_frame = frame
                elif (frame_index - stable_at) % stride == 0:
                    collected.append(frame)
                    if len(collected) >= samples_needed:
                        break

                frame_index += 1
        finally:
            self._terminate(process)

        if stable_at is None:
            logger.warning(
                f"[*] matchId={match_id}: camera not stable within "
                f"{config.GOAL_DETECTION_JUMP_STABILITY_TIMEOUT_SECONDS}s at the "
                f"{seek_seconds:.1f}s mark — skipping this sample point",
            )
        return collected

    @staticmethod
    def _probe_video(video_url: str) -> tuple[int, int, float, float]:
        probe = ffmpeg.probe(video_url)
        video_stream = next(s for s in probe["streams"] if s["codec_type"] == "video")
        width = int(video_stream["width"])
        height = int(video_stream["height"])
        num, den = video_stream["avg_frame_rate"].split("/")
        fps = float(num) / float(den) if float(den) != 0 else 25.0
        duration = float(probe["format"]["duration"])
        return width, height, fps, duration

    @staticmethod
    def _terminate(process) -> None:
        """Closing our end of the pipe makes ffmpeg's next write fail and
        exit on its own — used when we stop reading early (enough samples
        collected) rather than draining the rest of the capped window.
        """
        if process.stdout:
            process.stdout.close()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()

    def _defish(self, frame: np.ndarray) -> np.ndarray:
        h, w = frame.shape[:2]
        defisheyer = Defisheyer(coefficients=config.DEFISH_COEFFS, zoom_factor=config.DEFISH_ZOOM)
        defisheyer.initialize_maps(w, h)
        return defisheyer.undistort(frame)

    def _classify_corners(self, corners: list[Corner], frame_shape: tuple[int, ...]) -> dict:
        """Applies the two-case handling from docs/ai-collaboration-log.md
        (2026-09-27, 5. döntés): a corner outside the frame gets clamped to
        the edge automatically (no position would be more correct — a human
        couldn't place it any better); a corner the model had no confidence
        in (`None`) is surfaced as `needs_manual_review` with no guessed
        position, since a human *can* place it correctly and shouldn't be
        anchored to an invented coordinate.
        """
        height, width = frame_shape[:2]
        classified = []
        for corner in corners:
            if corner is None:
                classified.append({"x": None, "y": None, "source": "needs_manual_review"})
                continue

            x, y = corner
            if 0 <= x < width and 0 <= y < height:
                classified.append({"x": int(x), "y": int(y), "source": "detected"})
            else:
                clamped_x = min(max(x, 0), width - 1)
                clamped_y = min(max(y, 0), height - 1)
                classified.append({"x": int(clamped_x), "y": int(clamped_y), "source": "clamped"})
        return {"corners": classified}


def main():
    logging.basicConfig(
        level=getattr(logging, config.LOG_LEVEL.upper(), logging.INFO),
        format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
    )
    logger.info("[*] Booting up Goal Detection Pipeline...")

    minio_client = MinioClient()
    rmq_client = RabbitMQClient()
    rmq_client.connect()

    # Declare the output exchange up front so the first publish doesn't fail
    # if no consumer has bound to it yet (same as field_detection_pipeline).
    rmq_client.channel.exchange_declare(
        exchange=OUTPUT_EXCHANGE,
        exchange_type="topic",
        durable=True,
    )

    pipeline = GoalDetectionPipeline(minio_client, rmq_client)
    rmq_client.consume(queue_name=INPUT_QUEUE, callback=pipeline.handle_message)


if __name__ == "__main__":
    main()
