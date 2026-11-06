import json
import logging
import os
import tempfile
import uuid
from collections.abc import Iterator, Sequence

import cv2
import ffmpeg
import numpy as np
import supervision as sv

from config import config
from field_detection.corner_predictor import CornerPredictor
from field_detection.jersey_color_suggester import JerseyColorSuggester, SuggestedColors
from object_detection.detector import ObjectDetector
from util.court_zone import build_player_zone
from util.defisheyer import Defisheyer
from util.minio_client import MinioClient
from util.rabbitmq_client import RabbitMQClient

logger = logging.getLogger(__name__)

DEFISH_COEFFS = [0.1200, -0.0400, 0.0800, -0.1000]
DEFISH_ZOOM = 0.75

FRAME_TIMESTAMP_SECONDS = 180

INPUT_QUEUE = "field-detection-queue"
OUTPUT_EXCHANGE = "detection-exchange"
OUTPUT_ROUTING_KEY = "field.detected"

MINIO_BUCKET = "field-detection"


class FieldDetectionPipeline:
    def __init__(self, minio_client: MinioClient, rmq_client: RabbitMQClient):
        self.minio = minio_client
        self.rmq = rmq_client
        self.corner_predictor = CornerPredictor()
        self.color_suggester = self._build_color_suggester()

    @staticmethod
    def _build_color_suggester() -> JerseyColorSuggester | None:
        """The jersey-color suggestion is an extra: if it cannot be set up (e.g. the YOLO
        weights are missing in this container) the worker still detects the field."""
        if config.COLOR_SUGGESTION_FRAME_COUNT <= 0:
            logger.info("[*] Jersey-color suggestion disabled (COLOR_SUGGESTION_FRAME_COUNT<=0).")
            return None
        try:
            return JerseyColorSuggester(ObjectDetector())
        except Exception:
            logger.exception(
                "[!] Could not load the person detector; jersey-color suggestion is "
                "disabled, field detection keeps working.",
            )
            return None

    # Runs on a worker thread (RabbitMQClient.consume_long_running): the color suggestion
    # makes this handler last tens of seconds, far longer than a broker heartbeat.
    def handle_message(self, body: bytes):
        match_id = "UNKNOWN"
        temp_frame_path = None
        try:
            message = json.loads(body)
            video_url = message["videoUrl"]
            match_id = message["matchId"]

            logger.info(f"[➡] Field detection request for matchId={match_id}")

            duration = self._probe_duration(video_url)

            temp_frame_path = self._extract_frame(video_url, match_id, duration)
            frame = cv2.imread(temp_frame_path)
            if frame is None:
                msg = f"Failed to read extracted frame: {temp_frame_path}"
                raise RuntimeError(msg)

            defished = self._defish(frame)
            corners = self.corner_predictor.predict_corners(defished)

            defished_url = self._upload_image(defished, match_id)

            suggested_colors = self._suggest_colors(video_url, match_id, duration, corners)

            self.rmq.publish_from_worker(
                exchange=OUTPUT_EXCHANGE,
                routing_key=OUTPUT_ROUTING_KEY,
                message={
                    "matchId": match_id,
                    "status": "SUCCESS",
                    "defishedImageUrl": defished_url,
                    "corners": [{"x": int(x), "y": int(y)} for x, y in corners],
                    "suggestedColors": (
                        suggested_colors.to_message() if suggested_colors else None
                    ),
                },
            )
            logger.info(f"[⬅] Sent field.detected for matchId={match_id}")

        except Exception as e:
            logger.exception(f"[!] Field detection failed for matchId={match_id}")
            self.rmq.publish_from_worker(
                exchange=OUTPUT_EXCHANGE,
                routing_key=OUTPUT_ROUTING_KEY,
                message={
                    "matchId": match_id,
                    "status": "ERROR",
                    "errorMessage": str(e),
                },
            )
        finally:
            if temp_frame_path and os.path.exists(temp_frame_path):
                try:
                    os.remove(temp_frame_path)
                except OSError:
                    pass

    def _probe_duration(self, video_url: str) -> float | None:
        """Video duration in seconds, or None if ffprobe could not tell."""
        try:
            probe = ffmpeg.probe(video_url)
            return float(probe["format"]["duration"])
        except (ffmpeg.Error, KeyError, ValueError) as e:
            logger.warning(f"[*] ffprobe failed ({e}).")
            return None

    def _extract_frame(self, video_url: str, match_id: str, duration: float | None) -> str:
        """Pull a single frame at FRAME_TIMESTAMP_SECONDS without downloading the full MP4."""
        seek_seconds: float = FRAME_TIMESTAMP_SECONDS
        if duration is None:
            logger.warning("[*] Unknown duration; proceeding with default seek.")
        elif duration <= seek_seconds:
            seek_seconds = duration / 2.0
            logger.warning(
                f"[*] Video shorter than {FRAME_TIMESTAMP_SECONDS}s "
                f"({duration:.1f}s); seeking to midpoint {seek_seconds:.1f}s instead.",
            )
        return self._extract_frame_at(video_url, seek_seconds, match_id)

    def _extract_frame_at(self, video_url: str, seek_seconds: float, match_id: str) -> str:
        """Pull one frame at `seek_seconds` into a temp JPEG; the caller removes the file."""
        out_path = os.path.join(
            tempfile.gettempdir(),
            f"field_detect_{match_id}_{uuid.uuid4().hex}.jpg",
        )

        try:
            (
                ffmpeg.input(video_url, ss=seek_seconds)
                .output(out_path, vframes=1, format="image2", vcodec="mjpeg")
                .overwrite_output()
                .run(capture_stdout=True, capture_stderr=True)
            )
        except ffmpeg.Error as e:
            stderr = e.stderr.decode("utf8", errors="ignore") if e.stderr else ""
            msg = f"ffmpeg frame extraction failed: {stderr}"
            raise RuntimeError(msg) from e

        if not os.path.exists(out_path) or os.path.getsize(out_path) == 0:
            raise RuntimeError("ffmpeg produced no frame output")

        return out_path

    def _suggest_colors(
        self,
        video_url: str,
        match_id: str,
        duration: float | None,
        corners: Sequence[tuple[int, int]] | None = None,
    ) -> SuggestedColors | None:
        """Suggest home/away/referee jersey colors. Never raises: a failed suggestion must
        not fail the field detection (the UI then falls back to its default colors).

        `corners` is the court polygon the field detector estimated (defished-frame pixels).
        If there is a usable one, only the people standing inside it are sampled -- the same
        filter the object-detection worker applies; without one everybody detected counts."""
        if self.color_suggester is None:
            return None
        if duration is None:
            logger.warning(
                f"[*] Unknown video duration; skipping color suggestion for matchId={match_id}",
            )
            return None
        try:
            suggested = self.color_suggester.suggest(
                self._iter_suggestion_frames(video_url, match_id, duration),
                self._court_zone(corners, match_id),
            )
        except Exception:
            logger.exception(f"[!] Color suggestion failed for matchId={match_id}")
            return None

        if suggested is None:
            logger.warning(f"[*] Color suggestion found too few samples for matchId={match_id}")
        else:
            logger.info(
                f"[*] Suggested colors for matchId={match_id}: home={suggested.home}, "
                f"away={suggested.away}, referee={suggested.referee} "
                f"({suggested.sample_count} samples)",
            )
        return suggested

    @staticmethod
    def _court_zone(
        corners: Sequence[tuple[int, int]] | None,
        match_id: str,
    ) -> sv.PolygonZone | None:
        """The on-court test for the suggestion, or None (= no filtering) if there is no usable polygon."""
        if not corners:
            return None
        try:
            return build_player_zone(corners)
        except Exception:
            logger.exception(
                f"[!] Unusable court polygon for matchId={match_id}; the colors are "
                "suggested without the on-court filter.",
            )
            return None

    def _iter_suggestion_frames(
        self,
        video_url: str,
        match_id: str,
        duration: float,
    ) -> Iterator[np.ndarray]:
        """Yield defished frames spread evenly across the video, one at a time (a 4K frame
        is ~25 MB, so they are never all kept in memory). A frame that cannot be read is
        skipped, not fatal."""
        count = config.COLOR_SUGGESTION_FRAME_COUNT
        start = config.COLOR_SUGGESTION_START_FRACTION
        end = config.COLOR_SUGGESTION_END_FRACTION
        fractions = np.linspace(start, end, count) if count > 1 else [(start + end) / 2.0]

        defisheyer: Defisheyer | None = None
        defisheyer_size: tuple[int, int] | None = None
        for fraction in fractions:
            path: str | None = None
            try:
                path = self._extract_frame_at(video_url, duration * float(fraction), match_id)
                raw = cv2.imread(path)
                if raw is None:
                    logger.warning(f"[*] Could not read sampled frame at {fraction:.2f}")
                    continue
                h, w = raw.shape[:2]
                if defisheyer is None or defisheyer_size != (w, h):
                    defisheyer = self._make_defisheyer(w, h)
                    defisheyer_size = (w, h)
                frame = defisheyer.undistort(raw)
            except Exception as e:
                logger.warning(f"[*] Skipping sampled frame at {fraction:.2f}: {e}")
                continue
            finally:
                if path and os.path.exists(path):
                    try:
                        os.remove(path)
                    except OSError:
                        pass
            yield frame

    @staticmethod
    def _make_defisheyer(width: int, height: int) -> Defisheyer:
        # Maps are tied to input resolution, so we rebuild per-video in case
        # different cameras stream at different resolutions.
        defisheyer = Defisheyer(coefficients=DEFISH_COEFFS, zoom_factor=DEFISH_ZOOM)
        defisheyer.initialize_maps(width, height)
        return defisheyer

    def _defish(self, frame: np.ndarray) -> np.ndarray:
        h, w = frame.shape[:2]
        return self._make_defisheyer(w, h).undistort(frame)

    def _upload_image(self, image: np.ndarray, match_id: str) -> str:
        success, buffer = cv2.imencode(".jpg", image)
        if not success:
            raise RuntimeError("Failed to JPEG-encode defished frame")
        return self.minio.upload_bytes(
            bucket_name=MINIO_BUCKET,
            object_name=f"{match_id}.jpg",
            data=buffer.tobytes(),
            content_type="image/jpeg",
        )


def main():
    logging.basicConfig(
        level=getattr(logging, config.LOG_LEVEL.upper(), logging.INFO),
        format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
    )
    logger.info("[*] Booting up Field Detection Pipeline...")

    minio_client = MinioClient()
    rmq_client = RabbitMQClient()
    rmq_client.connect()

    # Declare the output exchange up front so the first publish doesn't fail
    # if no consumer has bound to it yet.
    rmq_client.channel.exchange_declare(
        exchange=OUTPUT_EXCHANGE,
        exchange_type="topic",
        durable=True,
    )

    pipeline = FieldDetectionPipeline(minio_client, rmq_client)

    rmq_client.consume_long_running(queue_name=INPUT_QUEUE, callback=pipeline.handle_message)


if __name__ == "__main__":
    main()
