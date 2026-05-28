import json
import logging
import os
import tempfile
import uuid

from config import config
from object_detection.detector import ObjectDetector
from object_detection.video_processor import VideoFrameProcessor
from util.minio_client import MinioClient
from util.rabbitmq_client import RabbitMQClient

logger = logging.getLogger(__name__)

INPUT_QUEUE = "video-processing-queue"
OUTPUT_EXCHANGE = "video-exchange"
OUTPUT_ROUTING_KEY = "video.completed"


def _require_color(message: dict, key: str, match_id: str) -> str:
    """Surface a clean error when the Spring producer didn't include a color.

    Most often this means Spring was redeployed without picking up the latest
    `VideoProcessingStartMessage` schema — the keys are simply absent from the
    JSON, and a bare `message[key]` would throw an opaque KeyError.
    """
    value = message.get(key)
    if not value:
        msg = (
            f"Required color field '{key}' is missing or blank in the "
            f"video.process message for matchId={match_id}. Has the Spring "
            f"backend been rebuilt with the new DTO?"
        )
        raise ValueError(msg)
    return value


class ObjectDetectionPipeline:
    def __init__(
        self,
        minio_client: MinioClient,
        rmq_client: RabbitMQClient,
        detector: ObjectDetector,
    ):
        self.minio = minio_client
        self.rmq = rmq_client
        self.detector = detector

    def handle_message(self, ch, method, properties, body):
        match_id = "UNKNOWN"
        temp_video_path: str | None = None
        try:
            message = json.loads(body)
            match_id = message.get("matchId", "UNKNOWN")
            bucket_name = message.get("bucketName") or config.RAW_VIDEOS_BUCKET
            file_name = message["fileName"]
            corners = [(int(c["x"]), int(c["y"])) for c in message["corners"]]
            home_hex = _require_color(message, "homeTeamColor", match_id)
            away_hex = _require_color(message, "awayTeamColor", match_id)
            referee_hex = _require_color(message, "refereeColor", match_id)

            logger.info(
                f"[➡] Object detection request for matchId={match_id}, file={file_name}",
            )
            logger.info(
                f"[*] Corners={corners}, "
                f"home={home_hex}, away={away_hex}, ref={referee_hex}",
            )

            temp_video_path = self._download_video(bucket_name, file_name, match_id)

            processor = VideoFrameProcessor(
                detector=self.detector,
                corners_xy=corners,
                home_hex=home_hex,
                away_hex=away_hex,
                referee_hex=referee_hex,
            )
            tracking = processor.process(temp_video_path)

            json_name = f"{match_id}.json"
            serialized = json.dumps(tracking)
            logger.info(
                f"[*] Tracking JSON ready: {len(serialized) / 1e6:.2f} MB "
                f"({len(tracking['trackingData'])} player rows, "
                f"{len(tracking['ballData'])} ball rows). Uploading...",
            )
            tracking_url = self.minio.upload_json(
                bucket_name=config.MINIO_BUCKET,
                object_name=json_name,
                data=serialized,
            )

            self.rmq.publish(
                exchange=OUTPUT_EXCHANGE,
                routing_key=OUTPUT_ROUTING_KEY,
                message={"matchId": match_id, "trackingDataUrl": tracking_url},
            )
            logger.info(f"[⬅] Sent video.completed for matchId={match_id}")

        except Exception:
            logger.exception(f"[!] Object detection failed for matchId={match_id}")
            # Spring's WorkerResultListener currently expects {matchId, trackingDataUrl};
            # publishing a status field is forward-compatible — Spring ignores unknown
            # fields when status is absent, and once Spring is extended it can branch
            # on this.
            self.rmq.publish(
                exchange=OUTPUT_EXCHANGE,
                routing_key=OUTPUT_ROUTING_KEY,
                message={"matchId": match_id, "status": "ERROR"},
            )
        finally:
            if temp_video_path and os.path.exists(temp_video_path):
                try:
                    os.remove(temp_video_path)
                except OSError:
                    logger.warning(f"[!] Could not delete temp file {temp_video_path}")
            ch.basic_ack(delivery_tag=method.delivery_tag)

    def _download_video(self, bucket: str, key: str, match_id: str) -> str:
        suffix = os.path.splitext(key)[1] or ".mp4"
        out_path = os.path.join(
            tempfile.gettempdir(),
            f"obj_detect_{match_id}_{uuid.uuid4().hex}{suffix}",
        )
        self.minio.download_file(bucket, key, out_path)
        size = os.path.getsize(out_path)
        if size == 0:
            msg = f"Downloaded video is empty: {bucket}/{key}"
            raise RuntimeError(msg)
        logger.info(f"[*] Downloaded {size / 1e6:.1f} MB to {out_path}")
        return out_path


def main():
    logging.basicConfig(
        level=getattr(logging, config.LOG_LEVEL.upper(), logging.INFO),
        format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
    )
    logger.info("[*] Booting up Object Detection Pipeline...")

    detector = ObjectDetector()
    minio_client = MinioClient()
    rmq_client = RabbitMQClient()
    rmq_client.connect()

    pipeline = ObjectDetectionPipeline(minio_client, rmq_client, detector)
    rmq_client.consume(queue_name=INPUT_QUEUE, callback=pipeline.handle_message)


if __name__ == "__main__":
    main()
