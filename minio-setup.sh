#!/bin/sh

# Used by both docker-compose.yml and docker-compose-deploy.yml. The service accounts have no default:
# a deploy without its own secrets must not get known ones.
if [ -z "${MINIO_APP_ACCESS_KEY}" ] || [ -z "${MINIO_APP_SECRET_KEY}" ]; then
  echo "MINIO_APP_ACCESS_KEY and MINIO_APP_SECRET_KEY must be set." >&2
  exit 1
fi
if [ -z "${MINIO_WORKER_ACCESS_KEY}" ] || [ -z "${MINIO_WORKER_SECRET_KEY}" ]; then
  echo "MINIO_WORKER_ACCESS_KEY and MINIO_WORKER_SECRET_KEY must be set." >&2
  exit 1
fi
if [ "${MINIO_APP_ACCESS_KEY}" = "${MINIO_WORKER_ACCESS_KEY}" ]; then
  echo "MINIO_WORKER_ACCESS_KEY must differ from MINIO_APP_ACCESS_KEY." >&2
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

# 2. Create the two service accounts, or set the secret and the policy again on an existing one.
#    Both hang off the root user, so each gets its own policy; without one it would inherit root.
#    The admin API is denied by name in both: MinIO lets any key create a new service account for
#    its own parent unless that is denied, and such an account would have root's full access.

# Spring: every bucket and object, but none of the MinIO admin API (users, keys, policies, server config).
cat > /tmp/spring-policy.json <<'JSON'
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow", "Action": ["s3:*"], "Resource": ["arn:aws:s3:::*"] },
    { "Effect": "Deny", "Action": ["admin:*"] }
  ]
}
JSON

# Workers (encoder, clip renderer, field detection, object detection): they read the uploaded video
# and the clip inputs, and write their results. They work on files users upload, so they get nothing
# in medical-docs and club-documents. ListBucket is there so a missing object answers 404, not 403
# (the clip renderer tells "no audio track" from that).
cat > /tmp/worker-policy.json <<'JSON'
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:GetObject"],
      "Resource": ["arn:aws:s3:::raw-videos/*", "arn:aws:s3:::clips/*"]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject"],
      "Resource": [
        "arn:aws:s3:::hls-streams/*",
        "arn:aws:s3:::tracking-data/*",
        "arn:aws:s3:::field-detection/*",
        "arn:aws:s3:::clips/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": ["s3:ListBucket"],
      "Resource": ["arn:aws:s3:::raw-videos", "arn:aws:s3:::clips"]
    },
    { "Effect": "Deny", "Action": ["admin:*"] }
  ]
}
JSON

ensure_service_account() {
  if mc admin user svcacct info local "$1" >/dev/null 2>&1; then
    mc admin user svcacct edit local "$1" --secret-key "$2" --policy "$3"
  else
    mc admin user svcacct add local admin --access-key "$1" --secret-key "$2" --policy "$3"
  fi
}

set -e
ensure_service_account "${MINIO_APP_ACCESS_KEY}" "${MINIO_APP_SECRET_KEY}" /tmp/spring-policy.json
ensure_service_account "${MINIO_WORKER_ACCESS_KEY}" "${MINIO_WORKER_SECRET_KEY}" /tmp/worker-policy.json
set +e

# 3. Attach the Webhook to the raw-videos bucket
# (The webhook itself is now defined in the docker-compose environment variables!)
mc event add local/raw-videos arn:minio:sqs::spring:webhook --event put

echo "MinIO setup is complete!"