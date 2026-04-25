import json
import logging
import math
import os
import time

import boto3
import pika
from botocore.client import Config

logging.basicConfig(
    level=logging.INFO,  # <-- DEBUG for maximum detail
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
)
# load_dotenv()
logger = logging.getLogger("ML Worker")
logger.setLevel(logging.DEBUG)

logger.info("[*] Booting up Python ML Worker...")

RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
MINIO_HOST = os.getenv("MINIO_HOST", "localhost")

# 1. Connect to local MinIO using AWS SDK
s3 = boto3.client(
    "s3",
    endpoint_url=f"http://{MINIO_HOST}:9000",
    aws_access_key_id="SPRING_BOOT_USER",
    aws_secret_access_key="SuperSecretKey123",
    config=Config(signature_version="s3v4"),
    region_name="us-east-1",
)


def generate_tracking_data(player_number, x_growth, y_growth, frames):
    """
    Generates tracking data for a specified number of players and frames.
    """

    # Initialize the base dictionary
    tracking_dict = {
        "videoFps": 30,
        "trackingData": [],
        "labelData": [{"goal": [1000, 2000, 3000], "corner": [550, 2550]}],
    }

    # Loop through each frame we want to generate
    for current_frame in range(1, frames + 1):
        # Loop through each player within that frame
        for player_id in range(1, player_number + 1):
            # Calculate the starting positions.
            # Using player_id * 10 offset to give them different starting coordinates
            base_x1 = 230 + (player_id * 10)
            base_y1 = 110 + (player_id * 10)
            base_x2 = 310 + (player_id * 10)
            base_y2 = 164 + (player_id * 10)

            # Apply growth based on the current frame
            # (current_frame - 1) means no growth on frame 1
            x1 = base_x1 + (x_growth * (current_frame - 1))
            y1 = base_y1 + (y_growth * (current_frame - 1))
            x2 = base_x2 + (x_growth * (current_frame - 1))
            y2 = base_y2 + (y_growth * (current_frame - 1))

            # Pitch-space coordinates in metres (105 × 68 standard pitch)
            base_tx = 20.0 + player_id * 30.0
            base_ty = 17.0 + player_id * 17.0
            tx = base_tx + math.sin(current_frame / 20.0 + player_id) * 12
            ty = base_ty + math.cos(current_frame / 25.0 * player_id) * 8

            # Create the data dictionary for this player on this frame
            frame_data = {
                "frame": current_frame,
                "player_id": player_id,
                "x1": x1,
                "y1": y1,
                "x2": x2,
                "y2": y2,
                "tx": round(tx, 2),
                "ty": round(ty, 2),
            }

            # Add it to the trackingData list
            tracking_dict["trackingData"].append(frame_data)

    return tracking_dict


# 2. Define what happens when a message arrives
def process_video_callback(ch, method, properties, body):
    message = json.loads(body)
    file_name = message.get("fileName")

    match_id = os.path.splitext(file_name)[0]
    logger.info(f"\n[➡] Received processing request for: {file_name}")
    logger.info(f"[*] Simulating AI tracking analysis...")

    # Simulating heavy GPU ML workload
    time.sleep(10)

    # 3. Create fake ML tracking data
    mock_tracking_data = generate_tracking_data(
        player_number=2,
        x_growth=5,
        y_growth=2,
        frames=90,
    )
    json_payload = json.dumps(mock_tracking_data)
    json_file_name = f"{match_id}.json"

    # 4. Upload the JSON to the tracking-data bucket
    logger.info(f"[*] Uploading tracking results to MinIO: {json_file_name}")
    s3.put_object(
        Bucket="tracking-data",
        Key=json_file_name,
        Body=json_payload,
        ContentType="application/json",
    )

    # 5. Tell Spring Boot we are done
    tracking_url = f"http://localhost:9000/tracking-data/{json_file_name}"
    completion_msg = {"matchId": match_id, "trackingDataUrl": tracking_url}

    ch.basic_publish(
        exchange="video-exchange",
        routing_key="video.completed",
        body=json.dumps(completion_msg),
        properties=pika.BasicProperties(content_type="application/json"),
    )
    logger.info(
        f"[⬅] Sent completion notification back to Spring Boot:\n{str(completion_msg)}"
    )

    # 6. Acknowledge the message so RabbitMQ deletes it from the queue
    ch.basic_ack(delivery_tag=method.delivery_tag)


# 3. Connect to RabbitMQ and start listening
credentials = pika.PlainCredentials("admin", "password")

connection = None
while True:
    try:
        logger.info(f"[*] Attempting to connect to RabbitMQ at {RABBITMQ_HOST}...")
        connection = pika.BlockingConnection(
            pika.ConnectionParameters(RABBITMQ_HOST, 5672, "/", credentials)
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
channel.queue_declare(queue="video-processing-queue", durable=True)
channel.basic_consume(
    queue="video-processing-queue", on_message_callback=process_video_callback
)

logger.info(
    "[*] Python Worker is successfully connected to RabbitMQ. Waiting for messages. To exit press CTRL+C"
)
channel.start_consuming()
