#!/bin/sh

# Used by both docker-compose.yml and docker-compose-deploy.yml. The Spring service account has no default:
# a deploy without its own secret must not get a known one that reaches every bucket.
if [ -z "${MINIO_APP_ACCESS_KEY}" ] || [ -z "${MINIO_APP_SECRET_KEY}" ]; then
  echo "MINIO_APP_ACCESS_KEY and MINIO_APP_SECRET_KEY must be set." >&2
  exit 1
fi

echo "Waiting for MinIO to start..."
# Keep trying to connect until MinIO is fully booted.
until mc alias set local http://minio:9000 admin "${MINIO_ROOT_PASSWORD}"; do
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
# HLS segments cannot be served with a single presigned playlist URL, so the
# stream bucket stays world-readable. Clip and tracking objects are served
# through ACL-checked presigned URLs instead.
mc anonymous set download local/field-detection
mc anonymous set download local/hls-streams
mc anonymous set none local/clips
mc anonymous set none local/tracking-data
# Sport medical certificates: private, opened only through short-lived presigned URLs, no webhook.
mc anonymous set none local/medical-docs
# Encrypted at rest with the MINIO_KMS_SECRET_KEY of the minio service; this also covers presigned PUTs.
# Versioning stays off: in a versioned bucket a delete keeps the old version (MedicalStorageGuard refuses it).
mc encrypt set sse-s3 local/medical-docs
# The clubs' sport medical privacy notices (PDF): private and encrypted like medical-docs, no webhook.
mc anonymous set none local/club-documents
mc encrypt set sse-s3 local/club-documents

# 2. Create the Service Account keys for Spring Boot, or set the secret again on an existing account
if mc admin user svcacct info local "${MINIO_APP_ACCESS_KEY}" >/dev/null 2>&1; then
  mc admin user svcacct edit local "${MINIO_APP_ACCESS_KEY}" --secret-key "${MINIO_APP_SECRET_KEY}"
else
  mc admin user svcacct add local admin --access-key "${MINIO_APP_ACCESS_KEY}" --secret-key "${MINIO_APP_SECRET_KEY}"
fi

# 3. Attach the Webhook to the raw-videos bucket
# (The webhook itself is now defined in the docker-compose environment variables!)
mc event add local/raw-videos arn:minio:sqs::spring:webhook --event put

echo "MinIO setup is complete!"