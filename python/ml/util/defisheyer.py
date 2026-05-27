import cv2
import numpy as np
import supervision as sv


class Defisheyer:
    def __init__(self, coefficients: list, zoom_factor: float = 1.0):
        """
        Initializes the Defisheyer with distortion coefficients and zoom.

        Args:
            coefficients (list): The D array values, e.g., [k1, k2, k3, k4].
            zoom_factor (float): Zoom adjustment to crop black edges. Default is 1.0.
        """
        self.D = np.array(coefficients, dtype=np.float32)
        self.zoom_factor = zoom_factor

        # Placeholders for the pre-computed maps
        self.map1 = None
        self.map2 = None

    def initialize_maps(self, video_info: sv.VideoInfo):
        """
        Computes the rectification maps based on the target video's resolution.

        Must be called before processing frames.
        """
        w, h = video_info.width, video_info.height

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
        """Applies the pre-computed maps to defish a single frame."""
        if self.map1 is None or self.map2 is None:
            raise ValueError(
                "Maps not initialized. Call `initialize_maps(video_info)` first.",
            )

        return cv2.remap(
            frame,
            self.map1,
            self.map2,
            interpolation=cv2.INTER_LINEAR,
            borderMode=cv2.BORDER_CONSTANT,
        )
