import math

import cv2
import numpy as np


class CameraStabilityDetector:
    """Detects when a static camera has settled after being adjusted at the
    start of a recording. It is a real detector, not a fixed-time guess.

    Feed consecutive frames one at a time via `feed()`. It tracks a handful
    of high-contrast points (Shi-Tomasi corners) between the previous and
    current frame with Lucas-Kanade optical flow, then fits a RANSAC affine
    transform to the tracked points. RANSAC's inlier/outlier split is what
    separates genuine camera motion (all background points move together)
    from independently-moving foreground objects like players (which become
    outliers and get ignored) — a naive "did pixels change" check couldn't
    tell those apart.
    """

    def __init__(
        self,
        motion_threshold: float,
        required_stable_frames: int,
        max_feature_points: int = 200,
        min_feature_points: int = 6,
    ):
        self.motion_threshold = motion_threshold
        self.required_stable_frames = required_stable_frames
        self.max_feature_points = max_feature_points
        self.min_feature_points = min_feature_points
        self._stable_streak = 0

    def feed(self, frame_bgr: np.ndarray, prev_frame_bgr: np.ndarray | None) -> bool:
        """Feed one frame pair. Returns True once the required number of
        consecutive low-motion frame pairs has been reached.
        """
        if prev_frame_bgr is None:
            return False

        prev_gray = cv2.cvtColor(prev_frame_bgr, cv2.COLOR_BGR2GRAY)
        curr_gray = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2GRAY)

        motion = self._motion_magnitude(prev_gray, curr_gray)
        # `motion is None` means we couldn't measure this pair reliably (too
        # few trackable points survived) — treat that as "assume still
        # moving" rather than silently counting it as stable.
        if motion is None or motion >= self.motion_threshold:
            self._stable_streak = 0
            return False

        self._stable_streak += 1
        return self._stable_streak >= self.required_stable_frames

    def _motion_magnitude(self, prev_gray: np.ndarray, curr_gray: np.ndarray) -> float | None:
        prev_pts = cv2.goodFeaturesToTrack(
            prev_gray,
            maxCorners=self.max_feature_points,
            qualityLevel=0.01,
            minDistance=7,
        )
        if prev_pts is None or len(prev_pts) < self.min_feature_points:
            return None

        curr_pts, status, _ = cv2.calcOpticalFlowPyrLK(prev_gray, curr_gray, prev_pts, None)
        if curr_pts is None or status is None:
            return None

        matched = status.flatten() == 1
        good_prev = prev_pts[matched]
        good_curr = curr_pts[matched]
        if len(good_prev) < self.min_feature_points:
            return None

        transform, _inlier_mask = cv2.estimateAffinePartial2D(
            good_prev, good_curr, method=cv2.RANSAC,
        )
        if transform is None:
            return None

        translation_mag = float(np.linalg.norm(transform[:, 2]))

        # estimateAffinePartial2D fits a similarity transform (translation +
        # rotation + UNIFORM SCALE) -- a zoom shows up purely as a deviation
        # of this scale factor from 1.0, with ~zero translation. Ignoring it
        # (as an earlier version of this method did) makes a zooming camera
        # look perfectly "stable" to a translation-only metric. The first
        # column's norm of the 2x2 block gives the scale directly.
        scale = float(np.linalg.norm(transform[:, 0]))
        half_diagonal = math.hypot(*prev_gray.shape[::-1]) / 2
        zoom_equivalent_motion = abs(scale - 1.0) * half_diagonal

        return translation_mag + zoom_equivalent_motion
