import cv2
import numpy as np


class Defisheyer:
    """Per-frame fisheye-to-rectilinear remapping using pre-computed maps.

    The maps depend only on the input frame resolution + coefficients, so
    `initialize_maps(w, h)` is called once per video and `undistort` is fast
    (cv2.remap with pre-baked maps).
    """

    def __init__(self, coefficients: list, zoom_factor: float = 1.0):
        """
        Args:
            coefficients (list): The fisheye D-array, e.g. [k1, k2, k3, k4].
            zoom_factor (float): Zoom adjustment to crop black edges. 1.0 = no zoom.
        """
        self.D = np.array(coefficients, dtype=np.float32)
        self.zoom_factor = zoom_factor

        # Pre-computed by initialize_maps()
        self.map1 = None
        self.map2 = None

    def initialize_maps(self, width: int, height: int) -> None:
        """Compute the rectification maps for a given frame resolution."""
        w, h = width, height

        # 1. Reconstruct the Camera Matrix (K)
        K = np.array(
            [[w / 2, 0, w / 2], [0, w / 2, h / 2], [0, 0, 1]],
            dtype=np.float32,
        )

        # 2. Create the new Camera Matrix with the applied zoom
        K_new = K.copy()
        K_new[0, 0] *= self.zoom_factor
        K_new[1, 1] *= self.zoom_factor

        # 3. Pre-compute the rectification map
        self.map1, self.map2 = cv2.fisheye.initUndistortRectifyMap(
            K,
            self.D,
            np.eye(3),
            K_new,
            (w, h),
            cv2.CV_16SC2,
        )

    def undistort(self, frame: np.ndarray) -> np.ndarray:
        """Apply the pre-computed maps to defish a single frame."""
        if self.map1 is None or self.map2 is None:
            raise ValueError(
                "Maps not initialized. Call `initialize_maps(w, h)` first.",
            )

        return cv2.remap(
            frame,
            self.map1,
            self.map2,
            interpolation=cv2.INTER_LINEAR,
            borderMode=cv2.BORDER_CONSTANT,
        )
