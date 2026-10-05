import functools
import json
import logging
import threading
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

    def consume_long_running(self, queue_name: str, callback: Callable[[bytes], None]):
        """Run each message's callback on a worker thread so the IO loop keeps sending heartbeats."""
        if not self.channel:
            raise Exception("Channel not initialized. Call connect() first.")

        self.channel.queue_declare(queue=queue_name, durable=True)
        self.channel.basic_qos(prefetch_count=1)
        self.channel.basic_consume(
            queue=queue_name,
            on_message_callback=lambda ch, method, _props, body: self._start_worker(
                callback, ch, method, body,
            ),
        )
        logger.info(f"[*] Waiting for long-running messages in queue: '{queue_name}'")
        self.channel.start_consuming()

    def publish_from_worker(self, exchange: str, routing_key: str, message: dict):
        done = threading.Event()
        errors: list[Exception] = []

        def publish_on_io_thread():
            try:
                self.publish(exchange, routing_key, message)
            except Exception as e:
                errors.append(e)
            finally:
                done.set()

        self.connection.add_callback_threadsafe(publish_on_io_thread)
        done.wait()
        if errors:
            raise errors[0]

    def _start_worker(self, callback: Callable[[bytes], None], ch, method, body: bytes):
        threading.Thread(
            target=self._run_and_ack,
            args=(callback, ch, method, body),
            daemon=True,
        ).start()

    def _run_and_ack(self, callback: Callable[[bytes], None], ch, method, body: bytes):
        try:
            callback(body)
        finally:
            self.connection.add_callback_threadsafe(
                functools.partial(ch.basic_ack, delivery_tag=method.delivery_tag),
            )
