"""The "is this person on the court?" test, shared by the object-detection worker and the
jersey-color suggestion of the field-detection worker.

A detection counts as on the court if the BOTTOM_CENTER of its bounding box (roughly the feet) lies
inside the court polygon. Keeping the construction in one place guarantees that the colors suggested
from a few sampled frames come from the same population of people that `TeamClassifier` later
sees (see docs/ai-collaboration-log.md, 2026-11-06, the court-polygon filter of the suggestion).
"""

from collections.abc import Sequence

import numpy as np
import supervision as sv

# Fewer vertices cannot enclose an area.
MIN_POLYGON_VERTICES = 3


def build_player_zone(corners_xy: Sequence[tuple[int, int]]) -> sv.PolygonZone:
    """`PolygonZone` of the court corners (pixels of the defished frame, any vertex count >= 3)."""
    if len(corners_xy) < MIN_POLYGON_VERTICES:
        msg = f"A court polygon needs at least {MIN_POLYGON_VERTICES} corners, got {len(corners_xy)}"
        raise ValueError(msg)
    return sv.PolygonZone(
        polygon=np.array(corners_xy, dtype=np.int32),
        triggering_anchors=(sv.Position.BOTTOM_CENTER,),
    )
