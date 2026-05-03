import json
import logging
import math
import os
import random
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


def generate_tracking_data(num_players=12, frames=1800):
    tracking_dict = {
        "videoFps": 30,
        "trackingData": [],
        "labelData": [{
            "goal":      [540, 1620],
            "corner":    [180, 720, 1080, 1440],
            "freekick":  [360, 900, 1260],
            "highlight": [270, 810, 1350, 1530, 1710],
        }],
    }

    # Phase 1: seed + draw per-player parameters before any frame loop
    player_params = {}
    for player_id in range(1, num_players + 1):
        random.seed(player_id)

        box_w = random.randint(60, 90)
        box_h = random.randint(100, 140)

        # Team A (1–6) occupies left half; Team B (7–12) right half
        if player_id <= num_players // 2:
            tx_start = random.uniform(3.0, 50.0)
            vx_start = random.uniform(100.0, 960.0 - box_w)
        else:
            tx_start = random.uniform(55.0, 102.0)
            vx_start = random.uniform(960.0, 1820.0 - box_w)

        ty_start = random.uniform(3.0, 65.0)
        vy_start = random.uniform(100.0, 900.0 - box_h)

        tx_amp   = random.uniform(3.0, 12.0)
        ty_amp   = random.uniform(2.0, 8.0)
        tx_freq  = random.uniform(0.005, 0.025)
        ty_freq  = random.uniform(0.005, 0.025)
        tx_phase = random.uniform(0, 2 * math.pi)
        ty_phase = random.uniform(0, 2 * math.pi)

        vx_amp   = random.uniform(20.0, 80.0)
        vy_amp   = random.uniform(15.0, 50.0)
        vx_freq  = random.uniform(0.004, 0.020)
        vy_freq  = random.uniform(0.004, 0.020)
        vx_phase = random.uniform(0, 2 * math.pi)
        vy_phase = random.uniform(0, 2 * math.pi)

        player_params[player_id] = {
            "box_w": box_w, "box_h": box_h,
            "tx_start": tx_start, "ty_start": ty_start,
            "vx_start": vx_start, "vy_start": vy_start,
            "tx_amp": tx_amp, "ty_amp": ty_amp,
            "tx_freq": tx_freq, "ty_freq": ty_freq,
            "tx_phase": tx_phase, "ty_phase": ty_phase,
            "vx_amp": vx_amp, "vy_amp": vy_amp,
            "vx_freq": vx_freq, "vy_freq": vy_freq,
            "vx_phase": vx_phase, "vy_phase": vy_phase,
        }

    # Phase 2: generate frame entries
    for current_frame in range(1, frames + 1):
        for player_id in range(1, num_players + 1):
            p = player_params[player_id]
            f = current_frame

            cx = (p["vx_start"] + p["box_w"] / 2
                  + p["vx_amp"] * math.sin(p["vx_freq"] * f + p["vx_phase"]))
            cy = (p["vy_start"] + p["box_h"] / 2
                  + p["vy_amp"] * math.cos(p["vy_freq"] * f + p["vy_phase"]))

            tx = (p["tx_start"]
                  + p["tx_amp"] * math.sin(p["tx_freq"] * f + p["tx_phase"]))
            ty = (p["ty_start"]
                  + p["ty_amp"] * math.cos(p["ty_freq"] * f + p["ty_phase"]))

            tracking_dict["trackingData"].append({
                "frame":     current_frame,
                "player_id": player_id,
                "x1": round(cx - p["box_w"] / 2, 1),
                "y1": round(cy - p["box_h"] / 2, 1),
                "x2": round(cx + p["box_w"] / 2, 1),
                "y2": round(cy + p["box_h"] / 2, 1),
                "tx": round(max(0.0, min(105.0, tx)), 2),
                "ty": round(max(0.0, min(68.0,  ty)), 2),
            })

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
    mock_tracking_data = generate_tracking_data()
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
