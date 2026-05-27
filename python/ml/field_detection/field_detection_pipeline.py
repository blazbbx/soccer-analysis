import json
import logging
import os
import tempfile
import uuid

import cv2
import ffmpeg
import numpy as np
import supervision as sv

from config import config
from field_detection.corner_predictor import CornerPredictor
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

    def handle_message(self, ch, method, properties, body):
        match_id = "UNKNOWN"
        temp_frame_path = None
        try:
            message = json.loads(body)
            video_url = message["videoUrl"]
            match_id = message["matchId"]

            logger.info(f"[➡] Field detection request for matchId={match_id}")

            temp_frame_path = self._extract_frame(video_url, match_id)
            frame = cv2.imread(temp_frame_path)
            if frame is None:
                msg = f"Failed to read extracted frame: {temp_frame_path}"
                raise RuntimeError(msg)

            defished = self._defish(frame)
            corners = self.corner_predictor.predict_corners(defished)

            defished_url = self._upload_image(defished, match_id)

            self.rmq.publish(
                exchange=OUTPUT_EXCHANGE,
                routing_key=OUTPUT_ROUTING_KEY,
                message={
                    "matchId": match_id,
                    "status": "SUCCESS",
                    "defishedImageUrl": defished_url,
                    "corners": [{"x": int(x), "y": int(y)} for x, y in corners],
                },
            )
            logger.info(f"[⬅] Sent field.detected for matchId={match_id}")

        except Exception as e:
            logger.exception(f"[!] Field detection failed for matchId={match_id}")
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
            if temp_frame_path and os.path.exists(temp_frame_path):
                try:
                    os.remove(temp_frame_path)
                except OSError:
                    pass
            ch.basic_ack(delivery_tag=method.delivery_tag)

    def _extract_frame(self, video_url: str, match_id: str) -> str:
        """Pull a single frame at FRAME_TIMESTAMP_SECONDS without downloading the full MP4."""
        seek_seconds: float = FRAME_TIMESTAMP_SECONDS
        try:
            probe = ffmpeg.probe(video_url)
            duration = float(probe["format"]["duration"])
            if duration <= seek_seconds:
                seek_seconds = duration / 2.0
                logger.warning(
                    f"[*] Video shorter than {FRAME_TIMESTAMP_SECONDS}s "
                    f"({duration:.1f}s); seeking to midpoint {seek_seconds:.1f}s instead.",
                )
        except (ffmpeg.Error, KeyError, ValueError) as e:
            logger.warning(f"[*] ffprobe failed ({e}); proceeding with default seek.")

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

    def _defish(self, frame: np.ndarray) -> np.ndarray:
        # Maps are tied to input resolution, so we rebuild per-video in case
        # different cameras stream at different resolutions.
        h, w = frame.shape[:2]
        video_info = sv.VideoInfo(width=w, height=h, fps=0)
        defisheyer = Defisheyer(coefficients=DEFISH_COEFFS, zoom_factor=DEFISH_ZOOM)
        defisheyer.initialize_maps(video_info)
        return defisheyer.undistort(frame)

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

    rmq_client.consume(queue_name=INPUT_QUEUE, callback=pipeline.handle_message)


if __name__ == "__main__":
    main()
