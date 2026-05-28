# Kept in sync with python/ml/util/defisheyer.py — the camera is fixed, so the
# code and coefficients are identical. Edit both together.

import cv2
import numpy as np


class Defisheyer:
    """Per-frame fisheye-to-rectilinear remapping using pre-computed maps."""

    def __init__(self, coefficients: list, zoom_factor: float = 1.0):
        self.D = np.array(coefficients, dtype=np.float32)
        self.zoom_factor = zoom_factor
        self.map1 = None
        self.map2 = None

    def initialize_maps(self, width: int, height: int) -> None:
        w, h = width, height
        K = np.array(
            [[w / 2, 0, w / 2], [0, w / 2, h / 2], [0, 0, 1]],
            dtype=np.float32,
        )
        K_new = K.copy()
        K_new[0, 0] *= self.zoom_factor
        K_new[1, 1] *= self.zoom_factor
        self.map1, self.map2 = cv2.fisheye.initUndistortRectifyMap(
            K,
            self.D,
            np.eye(3),
            K_new,
            (w, h),
            cv2.CV_16SC2,
        )

    def undistort(self, frame: np.ndarray) -> np.ndarray:
        if self.map1 is None or self.map2 is None:
            msg = "Maps not initialized. Call `initialize_maps(w, h)` first."
            raise ValueError(msg)
        return cv2.remap(
            frame,
            self.map1,
            self.map2,
            interpolation=cv2.INTER_LINEAR,
            borderMode=cv2.BORDER_CONSTANT,
        )
