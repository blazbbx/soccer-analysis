import logging

import numpy as np

from util.jersey_color import hex_to_lab, torso_mean_lab

logger = logging.getLogger(__name__)


# The labels a detection can get. The first three are the confirmed reference colors, in this
# order (np.argmin prefers the earlier one on an exact tie); "goalkeeper" is the rejection class.
_REFERENCE_LABELS = ("home", "away", "referee")
GOALKEEPER_LABEL = "goalkeeper"
_LABELS = (*_REFERENCE_LABELS, GOALKEEPER_LABEL)

# The rejection threshold: a detection whose torso color is farther than this (CIE76 delta E,
# i.e. Euclidean distance in CIELAB) from its NEAREST reference color is not trusted to belong to
# that class, and gets GOALKEEPER_LABEL instead. `None` = rejection off: every detection gets its
# nearest reference (the behaviour before this class existed).
#
# NOT CALIBRATED YET, hence off. The value has to come from a measurement on the labelled set
# (python/ml/scripts/build_team_label_set.py, label set team_label_set_v1): the share of true
# home / away / referee players that would be rejected vs the share of goalkeepers that would
# be kept. See docs/ai-collaboration-log.md (2026-11-07 entry on the goalkeeper class).
GOALKEEPER_MAX_DELTA_E: float | None = None


class TeamClassifier:
    """Per-detection nearest-reference-color classification in CIELAB space, with a reject option.

    The reference colors (home, away, referee) are the ones the user confirmed after the
    field-detection step suggested them. Each detection's color is the mean CIELAB color of
    the shoulder-to-waist band of its bounding box with the background left out (see
    `util.jersey_color`); the label is the nearest reference by Euclidean distance (~ perceived
    color difference).

    Goalkeepers have no reference color and no team. A detection that is too far from all three
    references (distance to the nearest one > `max_delta_e`) is not forced into one of them: it
    gets the label "goalkeeper". Under the project's assumptions this is the person who wears
    something else than the two teams and the referee; it also catches everything else that does
    not fit (a benched person at the sideline inside the court polygon, a blurred or occluded
    box), which is why the rate of true players that are rejected has to be measured.
    The detections reaching this class are already restricted to the user-confirmed court
    polygon (VideoFrameProcessor), so spectators outside the court never get here.

    The output stays a plain string in the tracking rows ("home" / "away" / "referee" /
    "goalkeeper"); only the frontend reads it, and it draws "goalkeeper" in a fixed color.

    No track-level voting -- player tracking is unstable in this pipeline (per
    project decision), so we classify each detection independently. Some
    flicker between classes on borderline frames is expected and accepted; with a hard
    threshold the flicker toward "goalkeeper" is the visible cost of rejection.
    """

    LABELS = _LABELS

    def __init__(
        self,
        home_hex: str,
        away_hex: str,
        referee_hex: str,
        max_delta_e: float | None = GOALKEEPER_MAX_DELTA_E,
    ):
        if max_delta_e is not None and max_delta_e <= 0:
            msg = f"max_delta_e must be positive or None (rejection off), got {max_delta_e}"
            raise ValueError(msg)
        self._ref_lab = np.stack([hex_to_lab(h) for h in (home_hex, away_hex, referee_hex)])
        self._max_delta_e = max_delta_e

    def classify(self, frame_bgr: np.ndarray, xyxy: np.ndarray) -> list[str]:
        """Return one label per row of `xyxy` (shape (N, 4))."""
        out: list[str] = []
        for x1, y1, x2, y2 in xyxy:
            color = torso_mean_lab(frame_bgr, x1, y1, x2, y2)
            if color is None:
                # No usable torso (box too small or all background): no color to compare.
                out.append(_REFERENCE_LABELS[0])
                continue
            dist = np.linalg.norm(self._ref_lab - color, axis=1)
            nearest = int(np.argmin(dist))
            if self._max_delta_e is not None and dist[nearest] > self._max_delta_e:
                out.append(GOALKEEPER_LABEL)
            else:
                out.append(_REFERENCE_LABELS[nearest])
        return out
