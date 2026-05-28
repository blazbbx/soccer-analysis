import logging

import boto3
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
        # Used only for URLs we hand back to Spring / the browser — it must
        # be reachable from outside the Docker network. Omits the port from the
        # URL when it's the scheme default (80 for http, 443 for https) so the
        # generated URLs are clean behind a reverse proxy.
        scheme = config.MINIO_PUBLIC_SCHEME
        host = config.MINIO_PUBLIC_HOST
        port = config.MINIO_PUBLIC_PORT
        is_default_port = (scheme == "http" and port == 80) or (
            scheme == "https" and port == 443
        )
        self._public_url_prefix = (
            f"{scheme}://{host}" if is_default_port else f"{scheme}://{host}:{port}"
        )

    def _public_url(self, bucket_name: str, object_name: str) -> str:
        return f"{self._public_url_prefix}/{bucket_name}/{object_name}"

    def upload_json(self, bucket_name: str, object_name: str, data: str):
        logger.info(f"[*] Uploading to MinIO: {bucket_name}/{object_name}")
        self.client.put_object(
            Bucket=bucket_name,
            Key=object_name,
            Body=data,
            ContentType="application/json",
        )
        return self._public_url(bucket_name, object_name)

    def upload_bytes(
        self,
        bucket_name: str,
        object_name: str,
        data: bytes,
        content_type: str = "application/octet-stream",
    ):
        logger.info(f"[*] Uploading to MinIO: {bucket_name}/{object_name}")
        self.client.put_object(
            Bucket=bucket_name,
            Key=object_name,
            Body=data,
            ContentType=content_type,
        )
        return self._public_url(bucket_name, object_name)

    def download_file(self, bucket_name: str, object_name: str, local_path: str) -> None:
        logger.info(f"[*] Downloading from MinIO: {bucket_name}/{object_name} -> {local_path}")
        self.client.download_file(bucket_name, object_name, local_path)
