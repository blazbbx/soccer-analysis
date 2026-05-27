import logging

import cv2
import numpy as np

logger = logging.getLogger(__name__)


# Order is intentional — also the index used by `classify`.
_LABELS = ("home", "away", "referee")


def _hex_to_bgr(hex_color: str) -> np.ndarray:
    """Convert '#RRGGBB' (or 'RRGGBB') to a uint8 BGR ndarray, shape (3,)."""
    s = hex_color.strip().lstrip("#")
    if len(s) != 6:
        msg = f"Bad hex color: {hex_color!r}"
        raise ValueError(msg)
    r = int(s[0:2], 16)
    g = int(s[2:4], 16)
    b = int(s[4:6], 16)
    return np.array([b, g, r], dtype=np.uint8)


class TeamClassifier:
    """Per-detection nearest-color classification in HSV space.

    No track-level voting — player tracking is unstable in this pipeline (per
    project decision), so we classify each detection independently. Some
    flicker between teams on borderline frames is expected and accepted.
    """

    LABELS = _LABELS

    def __init__(self, home_hex: str, away_hex: str, referee_hex: str):
        bgr = np.stack(
            [
                _hex_to_bgr(home_hex),
                _hex_to_bgr(away_hex),
                _hex_to_bgr(referee_hex),
            ],
        )
        # Reference colors in HSV for perceptually-fairer distance.
        # cv2 HSV: H in [0,180), S/V in [0,255].
        self._ref_hsv = cv2.cvtColor(bgr[None, :, :], cv2.COLOR_BGR2HSV)[0].astype(
            np.float32,
        )

    def classify(self, frame_bgr: np.ndarray, xyxy: np.ndarray) -> list[str]:
        """Return one label per row of `xyxy` (shape (N, 4))."""
        out: list[str] = []
        for x1, y1, x2, y2 in xyxy:
            crop = self._upper_torso_crop(frame_bgr, x1, y1, x2, y2)
            if crop is None or crop.size == 0:
                out.append(_LABELS[0])
                continue

            hsv = cv2.cvtColor(crop, cv2.COLOR_BGR2HSV).astype(np.float32)
            mean_hsv = hsv.reshape(-1, 3).mean(axis=0)

            # Hue wraps at 180 in OpenCV; take the shorter way around the circle.
            raw_dh = np.abs(mean_hsv[0] - self._ref_hsv[:, 0])
            dh = np.minimum(raw_dh, 180.0 - raw_dh)
            ds = np.abs(mean_hsv[1] - self._ref_hsv[:, 1])
            dv = np.abs(mean_hsv[2] - self._ref_hsv[:, 2])
            # Hue dominates the distance; saturation/value are tie-breakers.
            dist = dh * 2.0 + ds * 0.5 + dv * 0.5
            out.append(_LABELS[int(np.argmin(dist))])
        return out

    @staticmethod
    def _upper_torso_crop(
        frame: np.ndarray,
        x1: float,
        y1: float,
        x2: float,
        y2: float,
    ) -> np.ndarray | None:
        h, w = frame.shape[:2]
        bh = y2 - y1
        top = int(max(0, y1 + 0.25 * bh))
        bot = int(min(h, y1 + 0.75 * bh))
        left = int(max(0, x1))
        right = int(min(w, x2))
        if bot <= top or right <= left:
            return None
        return frame[top:bot, left:right]
