import logging
import os

import numpy as np

logger = logging.getLogger(__name__)

# Corner order is P1 top-left, P2 top-right, P3 bottom-left, P4 bottom-right (the tervezet's convention, and the
# keypoint order the pose model was trained with). A corner is `None` when the model has too little confidence in
# that keypoint: GoalDetectionPipeline turns that into "needs_manual_review" rather than guessing a position.
Corner = tuple[float, float] | None

# A pitch has two goals; the model is only ever asked for the two most confident detections.
MAX_GOALS = 2


class GoalPosePredictor:
    """Fine-tuned YOLOv8-Pose model (4 keypoints per goal, 1 class), see docs/goal-pose-training-experiments.md.

    The decision thresholds are fixed up front and not tuned on the measurement recordings
    (docs/goal-end-to-end-evaluation.md, section 6): `box_conf` is the detection confidence (the Ultralytics
    default), `kpt_conf` the per-keypoint confidence below which a corner is reported as `None`.
    """

    def __init__(
        self,
        weights_path: str,
        imgsz: int,
        box_conf: float,
        kpt_conf: float,
        device: str,
    ) -> None:
        # Imported here so the mock path (and any environment without the weights) does not pay for it.
        from ultralytics import YOLO

        self.model = YOLO(weights_path)
        self.weights_path = weights_path
        self.imgsz = imgsz
        self.box_conf = box_conf
        self.kpt_conf = kpt_conf
        self.device = device

    def predict_goals(self, image: np.ndarray) -> list[list[Corner]]:
        """Returns one entry per detected goal (at most two, left to right), each a list of 4 corners in pixel
        coordinates of `image`, ordered [P1, P2, P3, P4]. `image` is a BGR frame, as everywhere in the pipeline.
        """
        result = self.model.predict(
            image,
            imgsz=self.imgsz,
            conf=self.box_conf,
            device=self.device,
            verbose=False,
        )[0]
        if result.boxes is None or result.keypoints is None or len(result.boxes) == 0:
            return []

        box_scores = result.boxes.conf.cpu().numpy()
        box_center_x = result.boxes.xywh[:, 0].cpu().numpy()
        corner_xy = result.keypoints.xy.cpu().numpy()
        corner_conf = result.keypoints.conf
        corner_conf = None if corner_conf is None else corner_conf.cpu().numpy()

        keep = np.argsort(-box_scores)[:MAX_GOALS]
        keep = sorted(keep, key=lambda i: box_center_x[i])

        goals: list[list[Corner]] = []
        for i in keep:
            corners: list[Corner] = []
            for k in range(corner_xy.shape[1]):
                confident = corner_conf is None or corner_conf[i, k] >= self.kpt_conf
                corners.append((float(corner_xy[i, k, 0]), float(corner_xy[i, k, 1])) if confident else None)
            goals.append(corners)
        return goals


# Mirrors field_detection/corner_predictor.py's placeholder shape. It is what the worker uses when no trained
# weights are available, so the rest of the pipeline (stability detection, median, message publishing) can still
# be exercised. The coordinates are fake, a real deployment must have the weights (see load_goal_pose_predictor).
class MockGoalPosePredictor:
    def predict_goals(self, image: np.ndarray) -> list[list[Corner]]:
        height, width = image.shape[:2]
        margin_x = width // 8
        margin_y = height // 4
        post_width = width // 12

        left_goal: list[Corner] = [
            (margin_x, margin_y),
            (margin_x + post_width, margin_y),
            (margin_x, height - margin_y),
            (margin_x + post_width, height - margin_y),
        ]
        right_goal: list[Corner] = [
            (width - margin_x - post_width, margin_y),
            (width - margin_x, margin_y),
            (width - margin_x - post_width, height - margin_y),
            (width - margin_x, height - margin_y),
        ]
        return [left_goal, right_goal]


def load_goal_pose_predictor() -> GoalPosePredictor | MockGoalPosePredictor:
    """The real model if the weights at GOAL_POSE_MODEL_PATH exist, otherwise the mock, with a loud warning
    (the worker keeps running as before; the coordinates it then returns are NOT detections).
    """
    from config import config

    if os.path.isfile(config.GOAL_POSE_MODEL_PATH):
        logger.info(f"[*] Goal pose model: {config.GOAL_POSE_MODEL_PATH}")
        return GoalPosePredictor(
            weights_path=config.GOAL_POSE_MODEL_PATH,
            imgsz=config.GOAL_POSE_IMGSZ,
            box_conf=config.GOAL_POSE_BOX_CONF,
            kpt_conf=config.GOAL_POSE_KPT_CONF,
            device=config.GOAL_POSE_DEVICE,
        )
    logger.warning(
        f"[!] No goal pose weights at {config.GOAL_POSE_MODEL_PATH}: using the MOCK predictor. "
        "The returned goal corners are placeholders, not detections.",
    )
    return MockGoalPosePredictor()
