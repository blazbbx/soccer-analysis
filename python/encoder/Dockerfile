# syntax=docker/dockerfile:1.7
FROM python:3.13-slim

# OS-level deps:
#   - ffmpeg: required at runtime by ffmpeg-python (it shells out to the binary)
#   - libgl1 / libglib2.0-0: opencv-python's runtime shared libs (used for the
#     per-frame defish pass before HLS encoding)
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

# UV_LINK_MODE=copy avoids the hardlink fallback warning when uv's cache is on a
# different filesystem than /app (always the case when we mount the cache from
# BuildKit below).
ENV UV_LINK_MODE=copy

# Install Python deps first so the layer is cached unless lockfiles change.
# The BuildKit cache mount persists uv's download cache across rebuilds — so even
# when uv.lock changes (adding a dep, bumping a version), only the diff is fetched
# from PyPI instead of redownloading every wheel from scratch.
COPY pyproject.toml uv.lock ./
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --frozen --no-install-project

COPY . .

CMD ["uv", "run", "python", "main.py"]
