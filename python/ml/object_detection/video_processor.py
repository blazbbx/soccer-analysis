import json
import time
import os
import logging
from src.domain.tracking_mock import generate_tracking_data
from util.minio_client import MinioClient
from util.rabbitmq_client import RabbitMQClient
from config import config

logger = logging.getLogger(__name__)

class VideoProcessorService:
    def __init__(self, minio_client: MinioClient, rmq_client: RabbitMQClient):
        self.minio = minio_client
        self.rmq = rmq_client

    def handle_message(self, ch, method, properties, body):
        try:
            message = json.loads(body)
            file_name = message.get("fileName", "unknown.mp4")
            match_id = os.path.splitext(file_name)[0]
            
            logger.info(f"\n[➡] Received processing request for: {file_name}")
            logger.info("[*] Simulating AI tracking analysis...")
            time.sleep(10)  # Simulate GPU workload

            # 1. Generate data
            mock_tracking_data = generate_tracking_data()
            json_payload = json.dumps(mock_tracking_data)
            json_file_name = f"{match_id}.json"

            # 2. Upload to MinIO
            tracking_url = self.minio.upload_json(
                bucket_name=config.MINIO_BUCKET,
                object_name=json_file_name,
                data=json_payload
            )

            # 3. Publish completion
            completion_msg = {"matchId": match_id, "trackingDataUrl": tracking_url}
            self.rmq.publish(
                exchange="video-exchange",
                routing_key="video.completed",
                message=completion_msg
            )
            logger.info(f"[⬅] Sent completion notification:\n{completion_msg}")

            # 4. Acknowledge success
            ch.basic_ack(delivery_tag=method.delivery_tag)
            
        except Exception as e:
            logger.error(f"[!] Error processing message: {e}")
            # Optionally nack the message here so it requeues
            ch.basic_nack(delivery_tag=method.delivery_tag, requeue=False)