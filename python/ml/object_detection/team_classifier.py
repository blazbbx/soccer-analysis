import logging

import numpy as np

from util.jersey_color import hex_to_lab, torso_mean_lab

logger = logging.getLogger(__name__)


# Order is intentional — the labels a detection can get.
_LABELS = ("home", "away", "referee")


class TeamClassifier:
    """Per-detection nearest-reference-color classification in CIELAB space.

    The reference colors (home, away, referee) are the ones the user confirmed after the
    field-detection step suggested them. Each detection's color is the mean CIELAB color of
    the shoulder-to-waist band of its bounding box with the background left out (see
    `util.jersey_color`); the label is the nearest
    reference by Euclidean distance (~ perceived color difference).

    The goalkeepers may wear a different kit than their team. The user can confirm a
    reference color for each of them (optional): such a color is one more reference, but its
    label is the goalkeeper's TEAM ("home" / "away"), not a separate class, so everything after
    the classifier (tracking rows, minimap, events) is unchanged. Without a goalkeeper color
    the goalkeeper is treated like any other player of the team.

    No track-level voting — player tracking is unstable in this pipeline (per
    project decision), so we classify each detection independently. Some
    flicker between teams on borderline frames is expected and accepted.
    """

    LABELS = _LABELS

    def __init__(
        self,
        home_hex: str,
        away_hex: str,
        referee_hex: str,
        home_goalkeeper_hex: str | None = None,
        away_goalkeeper_hex: str | None = None,
    ):
        # (reference color, label). On an exact tie the earlier entry wins (np.argmin), so the
        # team and referee colors take precedence over the optional goalkeeper colors.
        references = [(home_hex, "home"), (away_hex, "away"), (referee_hex, "referee")]
        if home_goalkeeper_hex:
            references.append((home_goalkeeper_hex, "home"))
        if away_goalkeeper_hex:
            references.append((away_goalkeeper_hex, "away"))
        self._ref_lab = np.stack([hex_to_lab(hex_color) for hex_color, _ in references])
        self._ref_labels = tuple(label for _, label in references)

    def classify(self, frame_bgr: np.ndarray, xyxy: np.ndarray) -> list[str]:
        """Return one label per row of `xyxy` (shape (N, 4))."""
        out: list[str] = []
        for x1, y1, x2, y2 in xyxy:
            color = torso_mean_lab(frame_bgr, x1, y1, x2, y2)
            if color is None:
                out.append(_LABELS[0])
                continue
            dist = np.linalg.norm(self._ref_lab - color, axis=1)
            out.append(self._ref_labels[int(np.argmin(dist))])
        return out
