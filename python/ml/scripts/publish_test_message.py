"""Publish a fake `video.process` message to the local RabbitMQ.

Lets you trigger the object-detection worker without going through the whole
Spring + frontend upload flow. The worker must already be running (e.g. via
`object_detection/local_run.bat`) and connected to the same broker.

Usage from this directory (uses the project's uv environment for pika):

    uv run python scripts/publish_test_message.py

Edit `MESSAGE` below for the values you want to test. The defaults mirror the
last message Spring sent during development.
"""
import json
import sys

import pika

# Match the local broker (defaults from docker-compose + config.py).
HOST = "localhost"
PORT = 5672
USER = "admin"
PASSWORD = "password"

EXCHANGE = "video-exchange"
ROUTING_KEY = "video.process"

MESSAGE = {
    "bucketName": "raw-videos",
    "fileName": "361ff61b-0c34-4b41-bf53-b4c6d2ce5e46.mp4",
    "action": "START-PROCESSING",
    "matchId": "361ff61b-0c34-4b41-bf53-b4c6d2ce5e46",
    "corners": [
        {"x": 581, "y": 600},
        {"x": 1360, "y": 617},
        {"x": 2226, "y": 931},
        {"x": -229, "y": 856},
    ],
    "homeTeamColor": "#fb5151",
    "awayTeamColor": "#289f46",
    "refereeColor": "#000000",
}


def main() -> int:
    creds = pika.PlainCredentials(USER, PASSWORD)
    params = pika.ConnectionParameters(
        host=HOST, port=PORT, virtual_host="/", credentials=creds,
    )
    try:
        connection = pika.BlockingConnection(params)
    except pika.exceptions.AMQPConnectionError as ex:
        print(
            f"Could not connect to RabbitMQ at {HOST}:{PORT} — is the dev "
            f"stack up? ({ex})",
            file=sys.stderr,
        )
        return 1

    try:
        channel = connection.channel()
        body = json.dumps(MESSAGE)
        channel.basic_publish(
            exchange=EXCHANGE,
            routing_key=ROUTING_KEY,
            body=body,
            properties=pika.BasicProperties(content_type="application/json"),
        )
        print(
            f"Published {len(body)} bytes to {EXCHANGE}/{ROUTING_KEY} "
            f"(matchId={MESSAGE['matchId']})",
        )
    finally:
        connection.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
