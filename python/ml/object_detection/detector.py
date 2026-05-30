import logging
from pathlib import Path

import numpy as np
import supervision as sv
from ultralytics import YOLO

from config import config

logger = logging.getLogger(__name__)


class ObjectDetector:
    """Thin wrapper around ultralytics YOLO that runs one inference per frame
    and returns (players, ball) as separate `sv.Detections`.

    A single inference is run with the *minimum* of the player/ball confidence
    thresholds; the per-class subsets are then filtered with their own thresholds.
    This way the ball can be accepted at a much lower confidence than players
    without doing two forward passes.
    """

    def __init__(self, model_path: str | Path | None = None):
        path = str(model_path or config.YOLO_MODEL_PATH)
        logger.info(f"[*] Loading YOLO model from {path}")
        self.model = YOLO(path)
        
        # Move model to GPU if available, otherwise use CPU
        # ultralytics YOLO automatically detects and uses GPU when available
        # but we can be explicit: self.model.to('cuda') if torch.cuda.is_available()
        try:
            # Try to use GPU device 0
            logger.info("[*] Attempting to load model on GPU...")
            self.model.to('cuda')
            logger.info("[✓] Model loaded on GPU")
        except Exception as e:
            logger.warning(f"[!] Could not load on GPU: {e}, falling back to CPU")
            pass

        name_to_id = {name: idx for idx, name in self.model.names.items()}
        try:
            self.player_class = name_to_id[config.PLAYER_CLASS_NAME]
            self.ball_class = name_to_id[config.BALL_CLASS_NAME]
        except KeyError as e:
            msg = (
                f"Model classes do not include {e!s}. "
                f"Available classes: {self.model.names}"
            )
            raise RuntimeError(msg) from e

        logger.info(
            f"[*] Class ids resolved: player={self.player_class}, ball={self.ball_class}",
        )

    def detect(self, frame: np.ndarray) -> tuple[sv.Detections, sv.Detections]:
        min_conf = min(config.PLAYER_CONF, config.BALL_CONF)
        result = self.model.predict(
            frame,
            conf=min_conf,
            iou=config.NMS_IOU,
            verbose=False,
            device=0,  # Explicitly use GPU device 0
        )[0]
        detections = sv.Detections.from_ultralytics(result)

        players = detections[
            (detections.class_id == self.player_class)
            & (detections.confidence >= config.PLAYER_CONF)
        ]
        ball = detections[
            (detections.class_id == self.ball_class)
            & (detections.confidence >= config.BALL_CONF)
        ]
        return players, ball
