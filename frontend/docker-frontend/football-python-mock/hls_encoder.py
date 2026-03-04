import json
import logging
import os
import shutil
import subprocess
import time

import boto3
import pika
from botocore.client import Config

logging.basicConfig(
    level=logging.INFO,  # <-- DEBUG for maximum detail
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
)
# load_dotenv()
logger = logging.getLogger("Encoder worker")
logger.setLevel(logging.DEBUG)


logger.info("[*] Booting up Python HLS Encoder Worker...")

RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
MINIO_HOST = os.getenv("MINIO_HOST", "localhost")
# 1. Connect to MinIO
s3 = boto3.client(
    "s3",
    endpoint_url=f"http://{MINIO_HOST}:9000",
    aws_access_key_id="SPRING_BOOT_USER",
    aws_secret_access_key="SuperSecretKey123",
    config=Config(signature_version="s3v4"),
    region_name="us-east-1",
)

RAW_BUCKET = "raw-videos"
HLS_BUCKET = "hls-streams"


def process_encoding_callback(ch, method, properties, body):
    message = json.loads(body)
    file_name = message.get("fileName")
    base_name = file_name.replace(".mp4", "")  # e.g., "123-match"
    match_id = os.path.splitext(file_name)[0]

    logger.info(f"\n[➡] Received encoding request for: {file_name}")

    # 1. Create a temporary local workspace
    work_dir = f"./temp_{base_name}"
    os.makedirs(work_dir, exist_ok=True)
    local_mp4_path = f"{work_dir}/{file_name}"

    try:
        # 2. Download the massive MP4 from MinIO
        logger.info(f"[*] Downloading {file_name} from MinIO...")
        s3.download_file(RAW_BUCKET, file_name, local_mp4_path)

        # 3. The FFmpeg Magic Command (Chops MP4 into HLS)
        logger.info(f"[*] Running FFmpeg to generate HLS streams...")
        m3u8_path = f"{work_dir}/playlist.m3u8"

        # This command:
        # - scales to 720p (for web performance)
        # - creates 5-second .ts chunks
        # - puts everything in the work_dir
        ffmpeg_cmd = [
            "ffmpeg",
            "-i",
            local_mp4_path,
            "-profile:v",
            "baseline",  # High compatibility
            "-level",
            "3.0",
            "-s",
            "1280x720",  # 720p resolution
            "-start_number",
            "0",
            "-hls_time",
            "5",  # 5 second chunks
            "-hls_list_size",
            "0",  # 0 means keep ALL chunks in the playlist
            "-f",
            "hls",
            m3u8_path,
        ]

        # Run FFmpeg and wait for it to finish
        subprocess.run(
            ffmpeg_cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        )

        # 4. Upload all generated HLS files (.m3u8 and .ts) to MinIO
        logger.info(f"[*] FFmpeg finished. Uploading chunks to MinIO public bucket...")

        # We put them in a folder named after the video so they don"t mix up!
        s3_folder_prefix = f"{base_name}/"

        for root, dirs, files in os.walk(work_dir):
            for file in files:
                if file.endswith(".m3u8") or file.endswith(".ts"):
                    local_file_path = os.path.join(root, file)
                    s3_key = f"{s3_folder_prefix}{file}"

                    content_type = (
                        "application/vnd.apple.mpegurl"
                        if file.endswith(".m3u8")
                        else "video/MP2T"
                    )

                    s3.put_object(
                        Bucket=HLS_BUCKET,
                        Key=s3_key,
                        Body=open(local_file_path, "rb"),
                        ContentType=content_type,
                    )

        # 5. Tell Spring Boot we are done!
        hls_url = f"http://localhost:9000/{HLS_BUCKET}/{s3_folder_prefix}playlist.m3u8"  # Send localhost to Spring (its outside docker)
        completion_msg = {
            "matchId": match_id,
            "hlsUrl": hls_url,
            "status": "SUCCESS",
        }

        ch.basic_publish(
            exchange="video-exchange",
            routing_key="video.encoded",  # NEW routing key for Spring to listen to!
            body=json.dumps(completion_msg),
            properties=pika.BasicProperties(content_type="application/json"),
        )
        logger.info(
            f"[⬅] Upload complete! Sent encoded URL to Spring Boot:\n{str(completion_msg)}"
        )

    except Exception as e:
        error_details = str(e)
        print(f"[!] PROCESSING FAILED: {error_details}")

        # Build the ERROR payload using matchId
        completion_msg = {
            "matchId": match_id,
            "status": "ERROR",
            "errorMessage": f"Worker failed: {error_details}",
        }

        # Send it back to Spring Boot
        ch.basic_publish(
            exchange="video-exchange",
            routing_key="encoder.completed",  # Change to ml.completed in your ML worker!
            body=json.dumps(completion_msg),
            properties=pika.BasicProperties(content_type="application/json"),
        )
    finally:
        # 6. Cleanup the massive 2GB files from the worker"s hard drive!
        logger.info("[*] Cleaning up local temporary files...")
        if os.path.exists(work_dir):
            shutil.rmtree(work_dir)

        # Acknowledge the message so RabbitMQ removes it
        ch.basic_ack(delivery_tag=method.delivery_tag)


# Connect to RabbitMQ
credentials = pika.PlainCredentials("admin", "password")

connection = None
while True:
    try:
        logger.info(f"[*] Attempting to connect to RabbitMQ at {RABBITMQ_HOST}...")
        connection = pika.BlockingConnection(
            pika.ConnectionParameters(
                RABBITMQ_HOST,
                5672,
                "/",
                credentials,
                heartbeat=0,
            ),
        )
        logger.info("[*] Successfully connected to RabbitMQ!")
        break
    except pika.exceptions.AMQPConnectionError:
        logger.info(
            f"[!] RabbitMQ at {RABBITMQ_HOST} is not ready yet. Retrying in 5 seconds..."
        )
        time.sleep(5)

channel = connection.channel()

# Assume we create a new queue for this
channel.queue_declare(queue="video-encoding-queue", durable=True)
channel.basic_consume(
    queue="video-encoding-queue", on_message_callback=process_encoding_callback
)

logger.info(
    "[*] Encoder Worker connected to RabbitMQ. Waiting for messages. Press CTRL+C to exit"
)
channel.start_consuming()
