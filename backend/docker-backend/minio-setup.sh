#!/bin/sh

echo "Waiting for MinIO to start..."
# Keep trying to connect until MinIO is fully booted.
until mc alias set local http://minio:9000 admin "${MINIO_ROOT_PASSWORD:=password123}"; do
  sleep 2
done

echo "MinIO is up! Configuring..."

# 1. Create the buckets (ignores error if they already exist)
mc mb local/raw-videos --ignore-existing
mc mb local/tracking-data --ignore-existing
mc mb local/hls-streams --ignore-existing
mc mb local/clips --ignore-existing
mc mb local/medical-docs --ignore-existing
mc mb local/club-documents --ignore-existing
mc mb local/field-detection --ignore-existing

mc anonymous set download local/hls-streams
mc anonymous set none local/clips
mc anonymous set none local/tracking-data
# Sport medical certificates: private, opened only through short-lived presigned URLs, no webhook.
mc anonymous set none local/medical-docs
mc anonymous set download local/field-detection
# Encrypted at rest with the MINIO_KMS_SECRET_KEY of the minio service; this also covers presigned PUTs.
mc encrypt set sse-s3 local/medical-docs
# The clubs' sport medical privacy notices (PDF): private and encrypted like medical-docs, no webhook.
mc anonymous set none local/club-documents
mc encrypt set sse-s3 local/club-documents

# 2. Create the Service Account keys for Spring Boot (dev defaults, the same as application.yml),
# or set the secret again on an existing account
MINIO_APP_ACCESS_KEY="${MINIO_APP_ACCESS_KEY:-SPRING_BOOT_USER}"
MINIO_APP_SECRET_KEY="${MINIO_APP_SECRET_KEY:-dev-only-medical-app-secret}"
if mc admin user svcacct info local "${MINIO_APP_ACCESS_KEY}" >/dev/null 2>&1; then
  mc admin user svcacct edit local "${MINIO_APP_ACCESS_KEY}" --secret-key "${MINIO_APP_SECRET_KEY}"
else
  mc admin user svcacct add local admin --access-key "${MINIO_APP_ACCESS_KEY}" --secret-key "${MINIO_APP_SECRET_KEY}"
fi

# 3. Attach the Webhook to the raw-videos bucket
# (The webhook itself is now defined in the docker-compose environment variables!)
mc event add local/raw-videos arn:minio:sqs::spring:webhook --event put

echo "MinIO setup is complete!"