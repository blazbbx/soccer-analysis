import os
from typing import ClassVar


class Config:
    # Logging
    LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO")

    # RabbitMQ
    RABBITMQ_HOST = os.getenv("RABBITMQ_HOST", "localhost")
    RABBITMQ_PORT = int(os.getenv("RABBITMQ_PORT", 5672))
    RABBITMQ_USER = os.getenv("RABBITMQ_USER", "admin")
    RABBITMQ_PASS = os.getenv("RABBITMQ_PASS", "password")

    # MinIO — `MINIO_HOST/PORT` is what the worker connects to (inside Docker:
    # the service name `minio`). `MINIO_PUBLIC_SCHEME/HOST/PORT` is what the
    # worker bakes into URLs it hands back to Spring / the browser — those
    # need to be reachable from outside Docker. For HTTPS through a reverse
    # proxy on a public domain, set scheme=https, host=<domain>, port=443.
    MINIO_HOST = os.getenv("MINIO_HOST", "localhost")
    MINIO_PORT = int(os.getenv("MINIO_PORT", 9000))
    MINIO_PUBLIC_SCHEME = os.getenv("MINIO_PUBLIC_SCHEME", "http")
    MINIO_PUBLIC_HOST = os.getenv("MINIO_PUBLIC_HOST", MINIO_HOST)
    MINIO_PUBLIC_PORT = int(os.getenv("MINIO_PUBLIC_PORT", MINIO_PORT))
    MINIO_ACCESS_KEY = os.getenv("MINIO_ACCESS_KEY", "SPRING_BOOT_USER")
    MINIO_SECRET_KEY = os.getenv("MINIO_SECRET_KEY", "SuperSecretKey123")
    MINIO_BUCKET = os.getenv("MINIO_BUCKET", "tracking-data")
    RAW_VIDEOS_BUCKET = os.getenv("RAW_VIDEOS_BUCKET", "raw-videos")

    # Defisheying — same coefficients the field-detection pipeline uses.
    DEFISH_COEFFS: ClassVar[list[float]] = [
        float(x)
        for x in os.getenv("DEFISH_COEFFS", "0.1200,-0.0400,0.0800,-0.1000").split(",")
    ]
    DEFISH_ZOOM = float(os.getenv("DEFISH_ZOOM", "0.75"))

    # YOLO model
    YOLO_MODEL_PATH = os.getenv("YOLO_MODEL_PATH", "ml_models/best.pt")
    PLAYER_CLASS_NAME = os.getenv("PLAYER_CLASS_NAME", "person")
    BALL_CLASS_NAME = os.getenv("BALL_CLASS_NAME", "sports ball")
    # Players run at standard confidence; ball recall is poor on the current weights,
    # so we accept much lower-confidence ball detections and let the event state
    # machine filter out spurious hits.
    PLAYER_CONF = float(os.getenv("PLAYER_CONF", "0.25"))
    BALL_CONF = float(os.getenv("BALL_CONF", "0.05"))
    NMS_IOU = float(os.getenv("NMS_IOU", "0.5"))

    # Inference runs on every frame so we never miss a ball detection. The emission
    # interval controls how many of those frames are written to the output JSON:
    # 1 = every frame, N = every Nth.
    EMISSION_INTERVAL = int(os.getenv("EMISSION_INTERVAL", "1"))

    # Resolution of the HLS video the frontend actually plays. Bboxes emitted by
    # the worker are scaled down from the native defished frame size into this
    # space so they line up with `video.videoWidth` / `video.videoHeight` in the
    # browser. These MUST match the `s=` argument in python/encoder/main.py.
    HLS_OUTPUT_WIDTH = int(os.getenv("HLS_OUTPUT_WIDTH", "1280"))
    HLS_OUTPUT_HEIGHT = int(os.getenv("HLS_OUTPUT_HEIGHT", "720"))

    # Minimap pixel dimensions. Must match the minimap image rendered by the frontend.
    MINIMAP_WIDTH = int(os.getenv("MINIMAP_WIDTH", "105"))
    MINIMAP_HEIGHT = int(os.getenv("MINIMAP_HEIGHT", "68"))

    # Event detection — positional constants as fractions of the minimap so the
    # rules are camera- and pitch-size-independent.
    GOAL_X_FRACTION_MIN = float(os.getenv("GOAL_X_FRACTION_MIN", "0.4625"))
    GOAL_X_FRACTION_MAX = float(os.getenv("GOAL_X_FRACTION_MAX", "0.5375"))
    GOAL_OUTSIDE_BAND_FRACTION = float(os.getenv("GOAL_OUTSIDE_BAND_FRACTION", "0.05"))
    CORNER_RADIUS_FRACTION = float(os.getenv("CORNER_RADIUS_FRACTION", "0.06"))
    BALL_STATIONARY_VEL_FRACTION = float(
        os.getenv("BALL_STATIONARY_VEL_FRACTION", "0.02"),
    )

    EVENT_WINDOW_FRAMES = int(os.getenv("EVENT_WINDOW_FRAMES", "90"))
    EVENT_MIN_HITS = int(os.getenv("EVENT_MIN_HITS", "3"))
    GOAL_REFRACTORY_FRAMES = int(os.getenv("GOAL_REFRACTORY_FRAMES", "900"))
    CORNER_REFRACTORY_FRAMES = int(os.getenv("CORNER_REFRACTORY_FRAMES", "150"))
    FREEKICK_REFRACTORY_FRAMES = int(os.getenv("FREEKICK_REFRACTORY_FRAMES", "150"))
    FREEKICK_STATIONARY_FRAMES = int(os.getenv("FREEKICK_STATIONARY_FRAMES", "60"))

    # ByteTrack
    PLAYER_TRACK_BUFFER = int(os.getenv("PLAYER_TRACK_BUFFER", "30"))
    BALL_TRACK_BUFFER = int(os.getenv("BALL_TRACK_BUFFER", "120"))


config = Config()
