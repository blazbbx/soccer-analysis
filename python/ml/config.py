import os

class Config:
    # Logging
    LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO")

    # RabbitMQ
    RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
    RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", 5672))
    RABBITMQ_USER = os.getenv("RABBITMQ_USER", "admin")
    RABBITMQ_PASS = os.getenv("RABBITMQ_PASS", "password")
    
    # MinIO
    MINIO_HOST = os.getenv("MINIO_HOST", "localhost")
    MINIO_PORT = int(os.getenv("MINIO_PORT", 9000))
    MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "SPRING_BOOT_USER")
    MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "SuperSecretKey123")
    MINIO_BUCKET = os.getenv("MINIO_BUCKET", "tracking-data")

config = Config()