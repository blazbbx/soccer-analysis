import logging
from config import config
from util.minio_client import MinioClient
from util.rabbitmq_client import RabbitMQClient
from object_detection.video_processor import VideoProcessorService

logging.basicConfig(
    level=getattr(logging, config.LOG_LEVEL.upper(), logging.INFO),
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
)
logger = logging.getLogger("ML Worker")

def main():
    logger.info("[*] Booting up Python ML Worker...")

    # Initialize infrastructure
    minio_client = MinioClient()
    rmq_client = RabbitMQClient()
    rmq_client.connect()

    # Initialize service orchestration
    processor = VideoProcessorService(minio_client, rmq_client)

    # Start consuming
    rmq_client.consume(
        queue_name="video-processing-queue",
        callback=processor.handle_message
    )

if __name__ == "__main__":
    main()