import json
import logging
import os
import shutil
import time

import boto3
import ffmpeg
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
MOCK_MODE = os.getenv("MOCK", "false").lower() in ("true", "1", "yes")

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


def encode_video_to_hls(minio_video_url: str, local_m3u8_path: str):
    """
    Streams a video directly from a MinIO URL and encodes it into HLS chunks locally.
    """
    logger.info(f"Starting HLS encode from URL: {minio_video_url}")

    try:
        (
            ffmpeg.input(minio_video_url)  # 🔥 Streams directly from the MinIO URL
            .output(
                local_m3u8_path,
                format="hls",
                # We use dictionary unpacking for arguments that have colons
                **{"profile:v": "baseline"},
                # video_profile="main",
                level="4.0",
                s="1280x720",
                start_number=0,
                hls_time=5,
                hls_list_size=0,
            )
            .overwrite_output()  # 🔥 Automatically applies the '-y' flag to prevent freezing
            .run(
                capture_stdout=True, capture_stderr=True
            )  # Traps the console spam instead of DEVNULL
        )
        logger.info("✅ Encoding completed successfully!")

    except ffmpeg.Error as e:
        # If it crashes, we now get the EXACT error message instead of a silent failure
        logger.error("❌ FFmpeg Encoding Failed!")
        logger.error(f"stdout: {e.stdout.decode('utf8', errors='ignore')}")
        logger.error(f"stderr: {e.stderr.decode('utf8', errors='ignore')}")
        raise RuntimeError("Video encoding failed") from e


def process_encoding_callback(ch, method, properties, body):
    work_dir = None
    match_id = "UNKNOWN"

    try:
        message = json.loads(body)
        file_name = message.get("fileName")
        match_id = os.path.splitext(file_name)[0]

        logger.info(f"\n[➡] Received encoding request for: {file_name}")

        if MOCK_MODE:
            logger.info(
                "[MOCK MODE ENABLED] Bypassing FFmpeg. Using local mock files...",
            )
            work_dir = "./mock-video"

            if not os.path.exists(work_dir):
                raise FileNotFoundError("Mock directory './mock-video' does not exist!")

        else:
            logger.info(
                "[*] Normal Mode: Generating Presigned URL and running FFmpeg..."
            )
            # 1. Generate URL
            minio_video_url = s3.generate_presigned_url(
                "get_object",
                Params={"Bucket": RAW_BUCKET, "Key": file_name},
                ExpiresIn=3600,
            )

            # 2. Setup Temp Dir
            work_dir = f"./temp_{match_id}"
            os.makedirs(work_dir, exist_ok=True)
            m3u8_path = f"{work_dir}/playlist.m3u8"

            # 3. Run FFmpeg
            encode_video_to_hls(minio_video_url, m3u8_path)

        # --- UPLOAD LOGIC (Runs for both Mock and Normal modes) ---
        logger.info(f"[*] Uploading HLS chunks from {work_dir} to MinIO...")
        s3_folder_prefix = f"{match_id}/"

        for root, dirs, files in os.walk(work_dir):
            for file in files:
                # Catch both .ts files and .m4s files (if you switched to CMAF!)
                if (
                    file.endswith(".m3u8")
                    or file.endswith(".ts")
                    or file.endswith(".m4s")
                ):
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

        # Send Success Message
        hls_url = f"http://localhost:9000/{HLS_BUCKET}/{s3_folder_prefix}playlist.m3u8"
        completion_msg = {
            "matchId": match_id,
            "hlsUrl": hls_url,
            "status": "SUCCESS",
        }

        ch.basic_publish(
            exchange="video-exchange",
            routing_key="video.encoded",
            body=json.dumps(completion_msg),
            properties=pika.BasicProperties(content_type="application/json"),
        )
        logger.info(f"[⬅] Upload complete! Sent to Spring Boot: {hls_url}")

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
            routing_key="video.encoded",  # Change to ml.completed in your ML worker!
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
