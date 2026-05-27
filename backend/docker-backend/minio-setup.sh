#!/bin/sh

echo "Waiting for MinIO to start..."
# Keep trying to connect until MinIO is fully booted.
until mc alias set local http://minio:9000 admin password123; do
  sleep 2
done

echo "MinIO is up! Configuring..."

# 1. Create the buckets (ignores error if they already exist)
mc mb local/raw-videos --ignore-existing
mc mb local/tracking-data --ignore-existing
mc mb local/hls-streams --ignore-existing
mc mb local/clips --ignore-existing
mc mb local/field-detection --ignore-existing

mc anonymous set download local/hls-streams
mc anonymous set download local/clips
mc anonymous set download local/tracking-data
mc anonymous set download local/field-detection
# 2. Create the Service Account keys for Spring Boot
mc admin user svcacct add local admin --access-key "SPRING_BOOT_USER" --secret-key "SuperSecretKey123"

# 3. Attach the Webhook to the raw-videos bucket
# (The webhook itself is now defined in the docker-compose environment variables!)
mc event add local/raw-videos arn:minio:sqs::spring:webhook --event put

echo "MinIO setup is complete!"