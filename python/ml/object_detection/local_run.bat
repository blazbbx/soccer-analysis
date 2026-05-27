@echo off
REM Run python/ml/object_detection/main.py outside Docker.
REM
REM Requirements:
REM   - uv installed (https://docs.astral.sh/uv/)
REM   - Supporting infra (RabbitMQ + MinIO) reachable on localhost. The defaults
REM     in config.py already point at localhost, so `docker compose up -d` from
REM     the repo root is enough.

setlocal

REM cd into python/ml/ so uv picks up pyproject.toml + uv.lock, and Python can
REM resolve top-level imports like `from config import config` (PYTHONPATH).
cd /d "%~dp0\.."
set "PYTHONPATH=%CD%"

uv run python object_detection\object_detection_pipeline.py %*

endlocal
