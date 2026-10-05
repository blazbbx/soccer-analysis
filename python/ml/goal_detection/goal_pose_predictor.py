import numpy as np

Corner = tuple[int, int] | None


# TODO: replace with a fine-tuned YOLOv8-Pose model once one exists (see
# docs/ai-collaboration-log.md, 2026-09-26/27, 2. and 5. döntés). The
# existing ml_models/best.pt is the player/ball object detector, not a pose
# model — a new model, trained on our own annotated goal-corner keypoints,
# is a separate task from this pipeline scaffolding.
#
# This mock mirrors field_detection/corner_predictor.py's placeholder shape
# so the rest of the pipeline (stability detection, median, message
# publishing) can be wired up and exercised before the model exists. A
# corner is `None` when a real model would report low/no confidence for
# that keypoint — GoalDetectionPipeline turns that into "needs_manual_review"
# rather than guessing a position. The mock never returns None; that branch
# only becomes reachable once real per-keypoint confidence is available.
class GoalPosePredictor:
    def predict_goals(self, image: np.ndarray) -> list[list[Corner]]:
        """Returns one entry per detected goal, each a list of 4 corners in
        pixel coordinates, ordered [P1 top-left, P2 top-right, P3 bottom-left,
        P4 bottom-right] as defined in the tervezet.
        """
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
