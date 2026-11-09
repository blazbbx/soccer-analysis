"""Shared jersey-color primitives (CIELAB) for the team classifier and the color suggester.

Both consumers MUST use the same torso sample and the same mean-color definition, otherwise
the suggested reference colors and the classifier's per-detection colors are measured in
different "units" and the nearest-reference decision stops being meaningful. Keeping them
here, in one module, is the single source of truth for that.

Color space: CIELAB (L* in [0, 100], a*/b* roughly in [-127, 127]), computed by OpenCV from
float32 BGR in [0, 1] (sRGB, D65). Euclidean distance in this space approximates perceived
color difference, there is no hue wrap-around, and achromatic colors (white/grey/black) are
well defined -- the reasons it replaced HSV.
"""

import cv2
import numpy as np

# The torso sample is the SHOULDER-TO-WAIST band of the player's bounding box (intent stated
# by the student, 2026-11-06). Fractions of the box height, measured from the top edge.
# Read off 14 real YOLO person crops (two videos of one venue, youth players): the head ends
# at ~15-18 %, the shoulders sit at ~18-22 %, the waist (top of the shorts) at ~40-50 %.
# 0.20-0.40 stays inside the jersey. The earlier "top third" (0-33 %) contained the head and
# hair, the original code's 25-75 % contained the shorts and thighs. One tunable constant
# pair; the values are an observation on one venue, not yet an evaluated choice.
TORSO_TOP_FRACTION = 0.20
TORSO_BOTTOM_FRACTION = 0.40

# Crops thinner than this (in pixels, either side) carry no usable color information.
MIN_CROP_SIDE_PX = 4

# Background suppression (student's decision 2026-11-06). The box is wider than the torso, so
# the band also contains the floor/wall behind the player; averaged in, it drags every jersey
# color towards the floor color. Per detection, the background color is the median Lab color of
# a strip on each side OUTSIDE the box at the same rows (stateless: the classifier and the
# suggester compute it the same way, no per-video state has to travel between the workers), and
# band pixels closer than BACKGROUND_DELTA_E to it are left out of the mean. On the one real
# video tried, this local ring, a global floor color and a temporal-median background all gave
# near-identical clusters; the local ring skipped the fewest samples. The numbers below were
# picked by eye on that video: observations, not an evaluated choice.
BACKGROUND_RING_WIDTH_FRACTION = 0.5  # each side strip: this fraction of the box width ...
BACKGROUND_RING_MIN_PX = 3  # ... but at least this many pixels
BACKGROUND_DELTA_E = 10.0  # Lab distance (~ 4 just-noticeable differences) below = background
# If fewer than this share of the band is non-background, the person is (nearly) as colored
# as their surroundings -- or the box holds no person at all.
MIN_FOREGROUND_FRACTION = 0.20


def hex_to_bgr(hex_color: str) -> np.ndarray:
    """Convert '#RRGGBB' (or 'RRGGBB') to a uint8 BGR ndarray, shape (3,)."""
    s = hex_color.strip().lstrip("#")
    if len(s) != 6:
        msg = f"Bad hex color: {hex_color!r}"
        raise ValueError(msg)
    r = int(s[0:2], 16)
    g = int(s[2:4], 16)
    b = int(s[4:6], 16)
    return np.array([b, g, r], dtype=np.uint8)


def bgr_to_lab(bgr: np.ndarray) -> np.ndarray:
    """Convert a uint8 BGR image (any HxWx3 shape) to float32 CIELAB, same shape."""
    img = bgr.astype(np.float32) / 255.0
    return cv2.cvtColor(img, cv2.COLOR_BGR2Lab)


def hex_to_lab(hex_color: str) -> np.ndarray:
    """Convert '#RRGGBB' to a float32 CIELAB ndarray, shape (3,)."""
    bgr = hex_to_bgr(hex_color).reshape(1, 1, 3)
    return bgr_to_lab(bgr).reshape(3)


def lab_to_hex(lab: np.ndarray) -> str:
    """Convert a CIELAB color (shape (3,)) to '#rrggbb', clipping out-of-gamut values."""
    lab_img = np.asarray(lab, dtype=np.float32).reshape(1, 1, 3)
    bgr = cv2.cvtColor(lab_img, cv2.COLOR_Lab2BGR).reshape(3)
    b, g, r = (int(round(float(np.clip(c, 0.0, 1.0)) * 255.0)) for c in bgr)
    return f"#{r:02x}{g:02x}{b:02x}"


def torso_bounds(
    frame_shape: tuple[int, ...],
    x1: float,
    y1: float,
    x2: float,
    y2: float,
) -> tuple[int, int, int, int] | None:
    """Pixel bounds (top, bottom, left, right) of the shoulder-to-waist band, or None if the
    band is too small to carry color information."""
    h, w = frame_shape[:2]
    bh = y2 - y1
    top = int(max(0, y1 + TORSO_TOP_FRACTION * bh))
    bot = int(min(h, y1 + TORSO_BOTTOM_FRACTION * bh))
    left = int(max(0, x1))
    right = int(min(w, x2))
    if bot - top < MIN_CROP_SIDE_PX or right - left < MIN_CROP_SIDE_PX:
        return None
    return top, bot, left, right


def torso_crop(
    frame_bgr: np.ndarray,
    x1: float,
    y1: float,
    x2: float,
    y2: float,
) -> np.ndarray | None:
    """Return the torso sample (shoulder-to-waist band of the box) or None if it is too small."""
    bounds = torso_bounds(frame_bgr.shape, x1, y1, x2, y2)
    if bounds is None:
        return None
    top, bot, left, right = bounds
    return frame_bgr[top:bot, left:right]


def mean_lab(crop_bgr: np.ndarray) -> np.ndarray:
    """Plain mean CIELAB color of a crop (average over the per-pixel Lab values), shape (3,)."""
    return bgr_to_lab(crop_bgr).reshape(-1, 3).mean(axis=0)


def torso_mean_lab(
    frame_bgr: np.ndarray,
    x1: float,
    y1: float,
    x2: float,
    y2: float,
    *,
    require_foreground: bool = False,
) -> np.ndarray | None:
    """Mean CIELAB color of a detection's torso band with the background left out.

    Returns None if the band is too small. If (almost) the whole band looks like the
    background (fewer than MIN_FOREGROUND_FRACTION of its pixels differ from it):
      - require_foreground=False (the classifier): the plain band mean -- a person dressed in
        the color of their surroundings really has that color, and every detection needs a
        value;
      - require_foreground=True (the color suggester): None -- such a sample carries no jersey
        information and would only pollute the clustering.
    Without usable context (the box touches both image sides) the plain band mean is returned.
    """
    bounds = torso_bounds(frame_bgr.shape, x1, y1, x2, y2)
    if bounds is None:
        return None
    top, bot, left, right = bounds
    width = frame_bgr.shape[1]

    ring = max(BACKGROUND_RING_MIN_PX, round(BACKGROUND_RING_WIDTH_FRACTION * (right - left)))
    strip_left = max(0, left - ring)
    strip_right = min(width, right + ring)
    lab = bgr_to_lab(frame_bgr[top:bot, strip_left:strip_right])

    torso = lab[:, left - strip_left : right - strip_left].reshape(-1, 3)
    ring_pixels = np.concatenate(
        [
            lab[:, : left - strip_left].reshape(-1, 3),
            lab[:, right - strip_left :].reshape(-1, 3),
        ],
    )
    plain = torso.mean(axis=0)
    if ring_pixels.shape[0] < BACKGROUND_RING_MIN_PX * (bot - top):
        return plain

    background = np.median(ring_pixels, axis=0)
    foreground = np.linalg.norm(torso - background, axis=1) > BACKGROUND_DELTA_E
    if foreground.mean() < MIN_FOREGROUND_FRACTION:
        return None if require_foreground else plain
    return torso[foreground].mean(axis=0)
