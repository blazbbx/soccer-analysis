#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Backup of the docker-compose-deploy.yml stack. See BACKUP.md for setup,
# scheduling and restore.
#
#   BACKUP_DIR=/mnt/backup ./backup.sh
#
# Run it from the repository folder (next to .env) on the server, while the
# stack is running. Every run writes BACKUP_DIR/<date_time>/ with:
#   football_db.dump       the application database (pg_dump custom format)
#   keycloak_db.dump       Keycloak: users, password hashes, roles, realm
#   minio-encrypted.tar.gz medical-docs and club-documents as MinIO stores them
#                          on disk: still encrypted, readable only with the
#                          MINIO_KMS_SECRET_KEY of .env (keep .env elsewhere!)
#   caddy_data.tar.gz      the HTTPS certificates and the internal CA
#   OK                     written last: a folder without it is incomplete
# and updates one incremental copy of the large buckets in
#   BACKUP_DIR/minio-mirror/<bucket>/   (raw videos, streams, clips, ...)
# Dated folders older than BACKUP_KEEP_DAYS are removed. The mirror keeps
# files deleted in the app (no --remove), so a mistaken delete is recoverable.
#
# Settings (environment variables):
#   BACKUP_DIR         required, where the backups go (another disk or a mount
#                      of another machine: a backup on the same disk dies with it)
#   BACKUP_KEEP_DAYS   default 14
#   BACKUP_MIRROR      default 1; 0 skips the large-bucket mirror
#   BACKUP_COPY_ENV    default 0; 1 also copies .env (all passwords and the KMS
#                      key!) into the dated folder. Only if BACKUP_DIR is itself
#                      protected; otherwise store .env in a password manager.
# Exit code is non-zero if any step failed.
# ---------------------------------------------------------------------------
set -euo pipefail
# Git Bash on Windows would rewrite the /data style paths below; no effect on Linux.
export MSYS_NO_PATHCONV=1

: "${BACKUP_DIR:?Set BACKUP_DIR, e.g. BACKUP_DIR=/mnt/backup ./backup.sh}"
KEEP_DAYS="${BACKUP_KEEP_DAYS:-14}"
MIRROR="${BACKUP_MIRROR:-1}"
COPY_ENV="${BACKUP_COPY_ENV:-0}"

# Container names from docker-compose-deploy.yml; overridable for a test setup.
POSTGRES="${BACKUP_POSTGRES_CONTAINER:-football-postgres}"
MINIO="${BACKUP_MINIO_CONTAINER:-football-minio}"
PROXY="${BACKUP_PROXY_CONTAINER:-football-proxy}"
ENCRYPTED_BUCKETS=(medical-docs club-documents)
MIRRORED_BUCKETS=(raw-videos hls-streams clips tracking-data field-detection)
MC_IMAGE=minio/mc:latest
TOOLS_IMAGE=alpine:3.20

cd "$(dirname "$0")"
log() { echo "$(date '+%F %T') backup: $*"; }

[ -f .env ] || { log "no .env next to this script"; exit 1; }
# Value after the first "=", without a trailing CR and without surrounding quotes (as compose reads it).
MINIO_ROOT_PASSWORD="$(grep -E '^MINIO_ROOT_PASSWORD=' .env | head -1 | cut -d= -f2- | tr -d '\r')"
MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD#\"}"; MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD%\"}"
MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD#\'}"; MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD%\'}"
[ -n "$MINIO_ROOT_PASSWORD" ] || { log "MINIO_ROOT_PASSWORD missing from .env"; exit 1; }
for c in "$POSTGRES" "$MINIO"; do
  [ "$(docker inspect -f '{{.State.Running}}' "$c" 2>/dev/null)" = true ] || { log "container $c is not running"; exit 1; }
done

mkdir -p "$BACKUP_DIR"
# Absolute path for the docker -v mounts (on Git Bash the Windows form, which Docker Desktop understands).
BACKUP_DIR="$(cd "$BACKUP_DIR" && (pwd -W 2>/dev/null || pwd))"
# One run at a time: a slow mirror must not overlap the next night's run.
exec 9>"$BACKUP_DIR/.backup.lock"
if command -v flock >/dev/null 2>&1; then
  flock -n 9 || { log "another backup is still running"; exit 1; }
fi

STAMP="$(date '+%Y-%m-%d_%H%M')"
TARGET="$BACKUP_DIR/$STAMP"
mkdir -p "$TARGET"
chmod 700 "$TARGET"
log "writing $TARGET"

# 1. Databases. pg_dump gives a consistent snapshot while the app runs.
for db in football_db keycloak_db; do
  docker exec "$POSTGRES" pg_dump -U admin -Fc "$db" > "$TARGET/$db.dump"
  [ -s "$TARGET/$db.dump" ] || { log "$db dump is empty"; exit 1; }
  log "$db: $(du -h "$TARGET/$db.dump" | cut -f1)"
done

# 2. Encrypted buckets, straight from the MinIO data folder: the files stay encrypted. Through the
#    S3 API MinIO would decrypt them, and the backup would hold the medical documents in clear text.
docker run --rm --volumes-from "$MINIO:ro" -v "$TARGET:/backup" "$TOOLS_IMAGE" \
  sh -c 'cd /data && present="" && for b in "$@"; do [ -d "$b" ] && present="$present $b"; done; tar czf /backup/minio-encrypted.tar.gz $present' \
  sh "${ENCRYPTED_BUCKETS[@]}"
log "encrypted buckets: $(du -h "$TARGET/minio-encrypted.tar.gz" | cut -f1)"

# 3. HTTPS certificates and the internal CA (a new CA would need its root installed again everywhere).
if [ "$(docker inspect -f '{{.State.Running}}' "$PROXY" 2>/dev/null)" = true ]; then
  docker run --rm --volumes-from "$PROXY:ro" -v "$TARGET:/backup" "$TOOLS_IMAGE" tar czf /backup/caddy_data.tar.gz -C /data .
  log "caddy_data saved"
else
  log "container $PROXY is not running, caddy_data skipped"
fi

if [ "$COPY_ENV" = 1 ]; then
  cp .env "$TARGET/env"
  chmod 600 "$TARGET/env"
  log ".env copied (it holds every password and the KMS key: protect this folder)"
fi

touch "$TARGET/OK"

# 4. Large buckets: one incremental copy, only new or changed objects travel.
if [ "$MIRROR" = 1 ]; then
  NETWORK="$(docker inspect -f '{{range $name, $_ := .NetworkSettings.Networks}}{{$name}} {{end}}' "$MINIO" | awk '{print $1}')"
  mkdir -p "$BACKUP_DIR/minio-mirror"
  # The password goes in as a variable, not inside a URL, so any character in it works.
  docker run --rm --network "$NETWORK" -v "$BACKUP_DIR/minio-mirror:/mirror" \
    -e MINIO_ROOT_PASSWORD="$MINIO_ROOT_PASSWORD" --entrypoint sh "$MC_IMAGE" \
    -c 'mc alias set src http://minio:9000 admin "$MINIO_ROOT_PASSWORD" >/dev/null || exit 1
        for b in "$@"; do mc mirror --quiet --overwrite "src/$b" "/mirror/$b" >/dev/null || exit 1; done' \
    sh "${MIRRORED_BUCKETS[@]}"
  log "large buckets mirrored to $BACKUP_DIR/minio-mirror"
fi

# 5. Retention: only finished dated folders; the mirror is not touched.
find "$BACKUP_DIR" -mindepth 1 -maxdepth 1 -type d -name '20*_*' -mtime +"$KEEP_DAYS" -print -exec rm -rf {} + \
  | sed 's/^/removed old backup: /' || true

log "done"
