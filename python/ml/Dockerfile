# syntax=docker/dockerfile:1.7
FROM python:3.13-slim

# OS-level deps. apt's cache is mounted so the .deb files survive image rebuilds.
# ffmpeg: required at runtime by ffmpeg-python (it shells out to the binary).
# libgl1 / libglib2.0-0: required by opencv (pulled in via supervision).
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    rm -f /etc/apt/apt.conf.d/docker-clean && \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        ffmpeg \
        libgl1 \
        libglib2.0-0

COPY --from=ghcr.io/astral-sh/uv:latest /uv /usr/local/bin/uv

WORKDIR /app

# Modules at /app (config.py, util/, field_detection/, object_detection/) are imported
# as top-level packages by the worker scripts (e.g. `from config import config`). Running
# `python field_detection/foo.py` only puts /app/field_detection/ on sys.path, so we add
# /app explicitly here.
#
# UV_LINK_MODE=copy avoids the hardlink fallback warning when uv's cache is on a
# different filesystem than /app (always true when we mount the cache from BuildKit).
ENV PYTHONPATH=/app \
    UV_LINK_MODE=copy

# Install Python deps first so the layer is cached unless lockfiles change.
# The BuildKit cache mount persists uv's download cache across rebuilds — so even
# when uv.lock changes (adding a dep, bumping a version), only the diff is fetched
# from PyPI instead of redownloading every wheel from scratch.
COPY pyproject.toml uv.lock ./
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --frozen --no-install-project

COPY . .

# Compose overrides this per service so the same image can run different pipelines.
CMD ["uv", "run", "python", "field_detection/field_detection_pipeline.py"]
