import json
import logging
import time
from typing import Callable

import pika

from config import config

logger = logging.getLogger(__name__)


class RabbitMQClient:
    def __init__(self):
        self.credentials = pika.PlainCredentials(
            config.RABBITMQ_USER, config.RABBITMQ_PASS
        )
        self.parameters = pika.ConnectionParameters(
            host=config.RABBITMQ_HOST,
            port=config.RABBITMQ_PORT,
            virtual_host="/",
            credentials=self.credentials,
        )
        self.connection = None
        self.channel = None

    def connect(self):
        while True:
            try:
                logger.info(
                    f"[*] Attempting to connect to RabbitMQ at {config.RABBITMQ_HOST}..."
                )
                self.connection = pika.BlockingConnection(self.parameters)
                self.channel = self.connection.channel()
                logger.info("[*] Successfully connected to RabbitMQ!")
                break
            except pika.exceptions.AMQPConnectionError:
                logger.warning(
                    f"[!] RabbitMQ not ready at {config.RABBITMQ_HOST}. Retrying in 5 seconds..."
                )
                time.sleep(5)

    def publish(self, exchange: str, routing_key: str, message: dict):
        if not self.channel:
            raise Exception("Channel not initialized. Call connect() first.")

        self.channel.basic_publish(
            exchange=exchange,
            routing_key=routing_key,
            body=json.dumps(message),
            properties=pika.BasicProperties(content_type="application/json"),
        )

    def consume(self, queue_name: str, callback: Callable):
        if not self.channel:
            raise Exception("Channel not initialized. Call connect() first.")

        self.channel.queue_declare(queue=queue_name, durable=True)
        self.channel.basic_consume(queue=queue_name, on_message_callback=callback)
        logger.info(f"[*] Waiting for messages in queue: '{queue_name}'")
        self.channel.start_consuming()
