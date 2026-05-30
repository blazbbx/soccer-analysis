import json
import logging
import os
import shutil
import tempfile
import threading
import time
import uuid

import boto3
import cv2
import ffmpeg
import pika
from botocore.client import Config

from defisheyer import Defisheyer

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s - %(message)s",
)
logger = logging.getLogger("Encoder worker")
logger.setLevel(logging.DEBUG)

logger.info("[*] Booting up Python HLS Encoder Worker...")

RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
MINIO_HOST = os.getenv("MINIO_HOST", "localhost")
# Public-facing scheme/host/port for URLs handed back to Spring / the browser.
# Defaults preserve the previous "localhost:9000" behaviour for dev.
MINIO_PUBLIC_SCHEME = os.getenv("MINIO_PUBLIC_SCHEME", "http")
MINIO_PUBLIC_HOST = os.getenv("MINIO_PUBLIC_HOST", MINIO_HOST)
MINIO_PUBLIC_PORT = int(os.getenv("MINIO_PUBLIC_PORT", "9000"))
MOCK_MODE = os.getenv("MOCK", "false").lower() in ("true", "1", "yes")

# Defish coefficients. Hardcoded because the camera is fixed; must match
# python/ml/config.py (DEFISH_COEFFS, DEFISH_ZOOM) for the ML worker's bboxes
# to line up with this defished video.
DEFISH_COEFFS = [
    float(x)
    for x in os.getenv("DEFISH_COEFFS", "0.1200,-0.0400,0.0800,-0.1000").split(",")
]
DEFISH_ZOOM = float(os.getenv("DEFISH_ZOOM", "0.75"))

# HLS output resolution. Hardcoded by request; the ML worker's
# HLS_OUTPUT_WIDTH / HLS_OUTPUT_HEIGHT must match these.
HLS_OUTPUT_WIDTH = int(os.getenv("HLS_OUTPUT_WIDTH", "1280"))
HLS_OUTPUT_HEIGHT = int(os.getenv("HLS_OUTPUT_HEIGHT", "720"))


def _public_minio_url(bucket: str, key: str) -> str:
    is_default = (MINIO_PUBLIC_SCHEME == "http" and MINIO_PUBLIC_PORT == 80) or (
        MINIO_PUBLIC_SCHEME == "https" and MINIO_PUBLIC_PORT == 443
    )
    host_part = (
        MINIO_PUBLIC_HOST if is_default else f"{MINIO_PUBLIC_HOST}:{MINIO_PUBLIC_PORT}"
    )
    return f"{MINIO_PUBLIC_SCHEME}://{host_part}/{bucket}/{key}"

MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "SPRING_BOOT_USER")
MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "SuperSecretKey123")

# Connect to MinIO
s3 = boto3.client(
    "s3",
    endpoint_url=f"http://{MINIO_HOST}:9000",
    aws_access_key_id=MINIO_ACCESS_KEY,
    aws_secret_access_key=MINIO_SECRET_KEY,
    config=Config(signature_version="s3v4"),
    region_name="us-east-1",
)

RAW_BUCKET = "raw-videos"
HLS_BUCKET = "hls-streams"


def _has_audio_stream(path: str) -> bool:
    try:
        probe = ffmpeg.probe(path)
        return any(s.get("codec_type") == "audio" for s in probe.get("streams", []))
    except ffmpeg.Error as e:
        stderr = e.stderr.decode("utf8", errors="ignore") if e.stderr else ""
        logger.warning(f"ffprobe failed ({stderr}); assuming no audio.")
        return False


def encode_video_to_hls(local_raw_path: str, local_m3u8_path: str) -> None:
    """Defish the raw fisheye video frame-by-frame, then pipe rectified
    frames to ffmpeg which writes the HLS playlist + segments.

    The ML worker also defisheys with the same coefficients, so its bboxes
    line up with what the browser plays.
    """
    cap = cv2.VideoCapture(local_raw_path)
    if not cap.isOpened():
        msg = f"Could not open video with OpenCV: {local_raw_path}"
        raise RuntimeError(msg)

    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT)) or 0
    logger.info(
        f"[*] Source video: {width}x{height} @ {fps:.2f}fps, {total_frames} frames",
    )

    defisheyer = Defisheyer(coefficients=DEFISH_COEFFS, zoom_factor=DEFISH_ZOOM)
    defisheyer.initialize_maps(width, height)
    logger.info("[*] Defish maps ready. Starting transcode...")

    has_audio = _has_audio_stream(local_raw_path)
    logger.info(f"[*] Source has audio: {has_audio}")

    video_in = ffmpeg.input(
        "pipe:0",
        format="rawvideo",
        pix_fmt="bgr24",
        s=f"{width}x{height}",
        framerate=fps,
    )
    output_kwargs = {
        "format": "hls",
        "profile:v": "baseline",
        "level": "4.0",
        # Required when feeding rawvideo bgr24 through libx264: without this
        # ffmpeg infers a 4:4:4 intermediate, which baseline H.264 doesn't
        # support ("baseline profile doesn't support 4:4:4").
        "pix_fmt": "yuv420p",
        "s": f"{HLS_OUTPUT_WIDTH}x{HLS_OUTPUT_HEIGHT}",
        "start_number": 0,
        "hls_time": 5,
        "hls_list_size": 0,
    }
    if has_audio:
        audio_in = ffmpeg.input(local_raw_path).audio
        out = ffmpeg.output(video_in, audio_in, local_m3u8_path, **output_kwargs)
    else:
        out = ffmpeg.output(video_in, local_m3u8_path, **output_kwargs)

    process = out.overwrite_output().run_async(pipe_stdin=True, pipe_stderr=True)

    # Read stderr in a background thread to prevent deadlock when buffers fill up
    stderr_lines = []
    def read_stderr():
        try:
            for line in iter(process.stderr.readline, b''):
                if line:
                    stderr_lines.append(line.decode('utf8', errors='ignore'))
        except:
            pass
    
    stderr_thread = threading.Thread(target=read_stderr, daemon=True)
    stderr_thread.start()

    frames_written = 0
    start_t = time.time()
    last_heartbeat = start_t
    try:
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            defished = defisheyer.undistort(frame)
            try:
                process.stdin.write(defished.tobytes())
            except BrokenPipeError:
                # ffmpeg died — re-raise after .wait() so we can read stderr.
                logger.error("[!] ffmpeg pipe broke mid-encode")
                break
            frames_written += 1
            
            # Send heartbeat every 30 seconds to keep RabbitMQ connection alive
            current_time = time.time()
            if current_time - last_heartbeat > 30:
                try:
                    connection.process_data_events()
                except Exception as hb_err:
                    logger.warning(f"[!] Heartbeat failed: {hb_err}")
                last_heartbeat = current_time
            
            if frames_written % 300 == 0:
                elapsed = time.time() - start_t
                fps_avg = frames_written / elapsed if elapsed > 0 else 0
                pct = (
                    (frames_written / total_frames * 100.0) if total_frames else 0.0
                )
                logger.info(
                    f"[*] Defish+encode: {frames_written}/{total_frames or '?'} "
                    f"({pct:.1f}%) — {fps_avg:.1f} fps avg",
                )
    finally:
        cap.release()
        try:
            if process.stdin and not process.stdin.closed:
                process.stdin.close()
        except BrokenPipeError:
            pass
        process.wait()
        
        # Wait for stderr reader thread
        stderr_thread.join(timeout=5)
        
        if process.returncode != 0:
            tail = '\n'.join(stderr_lines[-50:])
            logger.error(f"[!] ffmpeg exit {process.returncode}; stderr tail:\n{tail}")
            msg = f"ffmpeg failed with code {process.returncode}"
            raise RuntimeError(msg)

    elapsed = time.time() - start_t
    logger.info(
        f"[OK] Defish+encode complete: {frames_written} frames in {elapsed:.1f}s "
        f"({frames_written / elapsed:.1f} fps avg)",
    )


def process_encoding_callback(ch, method, properties, body):
    work_dir = None
    local_raw_path = None
    match_id = "UNKNOWN"

    try:
        message = json.loads(body)
        file_name = message.get("fileName")
        match_id = os.path.splitext(file_name)[0]

        logger.info(f"\n[➡] Received encoding request for: {file_name}")
        
        # ACK immediately after receiving message, before encoding starts
        # This prevents RabbitMQ from timing out during long encoding processes
        ch.basic_ack(delivery_tag=method.delivery_tag)

        if MOCK_MODE:
            logger.info(
                "[MOCK MODE ENABLED] Bypassing FFmpeg. Using local mock files...",
            )
            work_dir = "./mock-video"

            if not os.path.exists(work_dir):
                msg = "Mock directory './mock-video' does not exist!"
                raise FileNotFoundError(msg)

        else:
            logger.info("[*] Normal Mode: downloading raw video and defishing...")
            # 1. Download raw fisheye video. We need a local file because the
            # defish pass reads frames with cv2.VideoCapture (HTTP streaming
            # via cv2 is unreliable; ffmpeg-from-URL skipped because we now
            # need per-frame Python intervention).
            local_raw_path = os.path.join(
                tempfile.gettempdir(),
                f"encoder_raw_{match_id}_{uuid.uuid4().hex}{os.path.splitext(file_name)[1] or '.mp4'}",
            )
            s3.download_file(RAW_BUCKET, file_name, local_raw_path)
            size_mb = os.path.getsize(local_raw_path) / 1e6
            logger.info(f"[*] Downloaded {size_mb:.1f} MB raw video.")

            # 2. Setup Temp Dir
            work_dir = f"./temp_{match_id}"
            os.makedirs(work_dir, exist_ok=True)
            m3u8_path = f"{work_dir}/playlist.m3u8"

            # 3. Defish + HLS encode
            encode_video_to_hls(local_raw_path, m3u8_path)

        # --- UPLOAD LOGIC (Runs for both Mock and Normal modes) ---
        logger.info(f"[*] Uploading HLS chunks from {work_dir} to MinIO...")
        s3_folder_prefix = f"{match_id}/"

        for root, _dirs, files in os.walk(work_dir):
            for file in files:
                # Catch both .ts files and .m4s files (if you switched to CMAF!)
                if (
                    file.endswith(".m3u8")
                    or file.endswith(".ts")
                    or file.endswith(".m4s")
                ):
                    local_file_path = os.path.join(root, file)
                    s3_key = f"{s3_folder_prefix}{file}"

                    content_type = (
                        "application/vnd.apple.mpegurl"
                        if file.endswith(".m3u8")
                        else "video/MP2T"
                    )

                    with open(local_file_path, "rb") as fh:
                        s3.put_object(
                            Bucket=HLS_BUCKET,
                            Key=s3_key,
                            Body=fh,
                            ContentType=content_type,
                        )

        # Send Success Message
        hls_url = _public_minio_url(HLS_BUCKET, f"{s3_folder_prefix}playlist.m3u8")
        completion_msg = {
            "matchId": match_id,
            "hlsUrl": hls_url,
            "status": "SUCCESS",
        }

        ch.basic_publish(
            exchange="video-exchange",
            routing_key="video.encoded",
            body=json.dumps(completion_msg),
            properties=pika.BasicProperties(content_type="application/json"),
        )
        logger.info(f"[⬅] Upload complete! Sent to Spring Boot: {hls_url}")

    except Exception as e:
        error_details = str(e)
        logger.exception(f"[!] PROCESSING FAILED: {error_details}")

        completion_msg = {
            "matchId": match_id,
            "status": "ERROR",
            "errorMessage": f"Worker failed: {error_details}",
        }

        ch.basic_publish(
            exchange="video-exchange",
            routing_key="video.encoded",
            body=json.dumps(completion_msg),
            properties=pika.BasicProperties(content_type="application/json"),
        )
    finally:
        # Cleanup local temp files
        logger.info("[*] Cleaning up local temporary files...")
        if work_dir and not MOCK_MODE and os.path.exists(work_dir):
            shutil.rmtree(work_dir, ignore_errors=True)
        if local_raw_path and os.path.exists(local_raw_path):
            try:
                os.remove(local_raw_path)
            except OSError:
                logger.warning(f"Could not delete temp raw file: {local_raw_path}")

RABBITMQ_PASS = os.getenv("RABBITMQ_PASSWORD", "Password123!")

# Connect to RabbitMQ
credentials = pika.PlainCredentials("admin", RABBITMQ_PASS)

connection = None
while True:
    try:
        logger.info(f"[*] Attempting to connect to RabbitMQ at {RABBITMQ_HOST}...")
        connection = pika.BlockingConnection(
            pika.ConnectionParameters(
                RABBITMQ_HOST,
                5672,
                "/",
                credentials,
                heartbeat=60,  # Send heartbeat every 60 seconds to keep connection alive
            ),
        )
        logger.info("[*] Successfully connected to RabbitMQ!")
        break
    except pika.exceptions.AMQPConnectionError:
        logger.info(
            f"[!] RabbitMQ at {RABBITMQ_HOST} is not ready yet. Retrying in 5 seconds...",
        )
        time.sleep(5)

channel = connection.channel()

channel.queue_declare(queue="video-encoding-queue", durable=True)
channel.basic_consume(
    queue="video-encoding-queue", on_message_callback=process_encoding_callback,
)

logger.info(
    "[*] Encoder Worker connected to RabbitMQ. Waiting for messages. Press CTRL+C to exit",
)
channel.start_consuming()
