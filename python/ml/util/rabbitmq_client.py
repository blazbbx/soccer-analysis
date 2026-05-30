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
            heartbeat=60,  # Send heartbeat every 60 seconds to keep connection alive
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
        max_retries = 3
        for attempt in range(max_retries):
            try:
                if not self.channel or self.channel.is_closed:
                    logger.warning("[!] Channel is closed or not initialized. Reconnecting...")
                    self.connect()

                self.channel.basic_publish(
                    exchange=exchange,
                    routing_key=routing_key,
                    body=json.dumps(message),
                    properties=pika.BasicProperties(content_type="application/json"),
                )
                return  # Success
            except (pika.exceptions.ChannelWrongStateError, 
                    pika.exceptions.ConnectionWrongStateError,
                    pika.exceptions.StreamLostError,
                    pika.exceptions.AMQPConnectionError) as e:
                logger.warning(f"[!] RabbitMQ publish error (attempt {attempt+1}/{max_retries}): {e}")
                if attempt < max_retries - 1:
                    time.sleep(5)
                    self.connect()
                else:
                    logger.error(f"[!] Failed to publish after {max_retries} attempts")
                    raise

    def consume(self, queue_name: str, callback: Callable):
        if not self.channel:
            raise Exception("Channel not initialized. Call connect() first.")

        self.channel.queue_declare(queue=queue_name, durable=True)
        self.channel.basic_consume(queue=queue_name, on_message_callback=callback)
        logger.info(f"[*] Waiting for messages in queue: '{queue_name}'")
        
        while True:
            try:
                self.channel.start_consuming()
            except (pika.exceptions.ConnectionWrongStateError, 
                    pika.exceptions.ChannelWrongStateError,
                    pika.exceptions.AMQPConnectionError) as e:
                logger.warning(f"[!] Connection error during consume: {e}. Reconnecting...")
                time.sleep(5)
                self.connect()
                self.channel.queue_declare(queue=queue_name, durable=True)
                self.channel.basic_consume(queue=queue_name, on_message_callback=callback)
            except KeyboardInterrupt:
                logger.info("[*] Consumer interrupted by user")
                break
