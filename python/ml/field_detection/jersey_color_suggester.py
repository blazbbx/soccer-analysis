"""Suggests the home / away / referee jersey colors from a handful of video frames.

Method (decision 21, docs/ai-collaboration-log.md, 2026-11-06):
  1. For every sampled (defished) frame, detect people with the same YOLO model the
     object-detection worker uses. If the field detection has a court polygon, keep only the
     people whose feet (box bottom-centre) are inside it -- the filter the object-detection
     worker applies too (`util.court_zone`), so the suggestion is drawn from the same people the
     classifier will see.
  2. Each detection becomes ONE sample: the mean CIELAB color of the shoulder-to-waist band of its
     bounding box with the background left out (see `util.jersey_color`) -- exactly the
     feature `TeamClassifier` uses later, so the suggested reference colors and the
     classifier's inputs are in the same space. A box whose band is (almost) all background
     yields no sample here.
  3. K-means (k = 3, Lab space) over all samples. The cluster with the most members is
     proposed as "home", the second as "away", the third as "referee". The size order
     is only a convention -- the system cannot know which team is the home team, so the
     user can swap them (and edit all three colors) in the UI.

Known limitations (stated, not hidden): the court polygon known at suggestion time is the
field detector's estimate (still a stub that covers the whole frame, so then nothing is
filtered), not the corners the user confirms later; spectators and benched people sitting right
at the sideline pass the foot-point test; the referee often stands on or beyond the line and
is then dropped; goalkeepers (different kit) and the referee cannot be told apart by color
alone; the band still includes the arms/skin; the background suppression assumes the strip
beside the box is background (a neighbouring player there blurs it); the colors can come out
dark in dim halls.
"""

import logging
from collections.abc import Iterable
from dataclasses import dataclass

import cv2
import numpy as np
import supervision as sv

from config import config
from object_detection.detector import ObjectDetector
from util.jersey_color import lab_to_hex, torso_mean_lab

logger = logging.getLogger(__name__)

# Three roles: home, away, referee. Structural, not a tunable.
CLUSTER_COUNT = 3
# k-means++ restarts and the fixed seed that makes the suggestion reproducible.
KMEANS_ATTEMPTS = 10
KMEANS_SEED = 0
KMEANS_CRITERIA = (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 100, 0.01)


@dataclass(frozen=True)
class SuggestedColors:
    """Suggested reference colors as '#rrggbb', plus how many samples they came from."""

    home: str
    away: str
    referee: str
    sample_count: int

    def to_message(self) -> dict:
        return {"home": self.home, "away": self.away, "referee": self.referee}


def cluster_jersey_colors(samples_lab: np.ndarray) -> SuggestedColors | None:
    """K-means over CIELAB samples, shape (N, 3). Returns None if N is too small
    or the data cannot form three non-empty groups."""
    if samples_lab.shape[0] < max(config.COLOR_SUGGESTION_MIN_SAMPLES, CLUSTER_COUNT):
        return None

    data = np.ascontiguousarray(samples_lab, dtype=np.float32)
    cv2.setRNGSeed(KMEANS_SEED)
    _, labels, centers = cv2.kmeans(
        data,
        CLUSTER_COUNT,
        None,
        KMEANS_CRITERIA,
        KMEANS_ATTEMPTS,
        cv2.KMEANS_PP_CENTERS,
    )
    counts = np.bincount(labels.ravel(), minlength=CLUSTER_COUNT)
    if (counts == 0).any():
        return None

    order = np.argsort(-counts, kind="stable")
    home, away, referee = (lab_to_hex(centers[i]) for i in order)
    return SuggestedColors(home, away, referee, sample_count=int(data.shape[0]))


class JerseyColorSuggester:
    def __init__(self, detector: ObjectDetector):
        self.detector = detector

    def collect_samples(
        self,
        frames: Iterable[np.ndarray],
        player_zone: sv.PolygonZone | None = None,
    ) -> np.ndarray:
        """Mean torso CIELAB color of every person detected in `frames`, shape (N, 3).

        `frames` is consumed lazily, so a generator keeps only one frame in memory.
        With a `player_zone` (see `util.court_zone`) only people whose feet are inside the court
        polygon count -- the same filter the object-detection worker applies before it
        classifies anyone. Without one, everybody the detector finds counts."""
        samples: list[np.ndarray] = []
        detected = 0
        on_court = 0
        for frame in frames:
            players, _ = self.detector.detect(frame)
            detected += len(players)
            if player_zone is not None:
                players = players[player_zone.trigger(players)]
            on_court += len(players)
            for x1, y1, x2, y2 in players.xyxy:
                color = torso_mean_lab(frame, x1, y1, x2, y2, require_foreground=True)
                if color is not None:
                    samples.append(color)
        if player_zone is not None:
            logger.info(f"[*] Color suggestion: {on_court}/{detected} detections inside the court polygon")
        if not samples:
            return np.empty((0, 3), dtype=np.float32)
        return np.stack(samples).astype(np.float32)

    def suggest(
        self,
        frames: Iterable[np.ndarray],
        player_zone: sv.PolygonZone | None = None,
    ) -> SuggestedColors | None:
        samples = self.collect_samples(frames, player_zone)
        logger.info(f"[*] Color suggestion: {samples.shape[0]} torso samples collected")
        return cluster_jersey_colors(samples)
