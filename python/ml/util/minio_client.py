import boto3
import logging
from botocore.client import Config as BotoConfig
from config import config

logger = logging.getLogger(__name__)

class MinioClient:
    def __init__(self):
        endpoint = f"http://{config.MINIO_HOST}:{config.MINIO_PORT}"
        self.client = boto3.client(
            "s3",
            endpoint_url=endpoint,
            aws_access_key_id=config.MINIO_ACCESS_KEY,
            aws_secret_access_key=config.MINIO_SECRET_KEY,
            config=BotoConfig(signature_version="s3v4"),
            region_name="us-east-1",
        )

    def upload_json(self, bucket_name: str, object_name: str, data: str):
        logger.info(f"[*] Uploading to MinIO: {bucket_name}/{object_name}")
        self.client.put_object(
            Bucket=bucket_name,
            Key=object_name,
            Body=data,
            ContentType="application/json",
        )
        # Assuming MinIO structure for the return URL
        return f"http://{config.MINIO_HOST}:{config.MINIO_PORT}/{bucket_name}/{object_name}"