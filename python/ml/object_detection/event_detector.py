import logging
from collections import deque
from dataclasses import dataclass
from enum import Enum

from config import config

logger = logging.getLogger(__name__)


class EventType(str, Enum):
    GOAL = "goal"
    CORNER = "corner"
    FREEKICK = "freekick"


@dataclass
class _BallSample:
    frame: int
    x: float
    y: float


class EventDetector:
    """Sliding-window state machine over the ball's minimap trajectory.

    All event thresholds are derived from minimap dimensions (so the same rules
    apply regardless of the chosen minimap pixel size). Ball detections are
    sparse on the current weights, so events fire on "at least N hits in a
    window of M frames" rather than consecutive frames.
    """

    def __init__(self, minimap_w: int, minimap_h: int):
        self.w = minimap_w
        self.h = minimap_h

        self.goal_x_min = config.GOAL_X_FRACTION_MIN * minimap_w
        self.goal_x_max = config.GOAL_X_FRACTION_MAX * minimap_w
        self.outside_band = config.GOAL_OUTSIDE_BAND_FRACTION * minimap_h
        self.corner_radius_sq = (
            config.CORNER_RADIUS_FRACTION * min(minimap_w, minimap_h)
        ) ** 2

        # Pitch corners on the minimap rectangle, clockwise from top-left.
        self.corners = (
            (0.0, 0.0),
            (float(minimap_w), 0.0),
            (float(minimap_w), float(minimap_h)),
            (0.0, float(minimap_h)),
        )

        self.window: deque[_BallSample] = deque(maxlen=config.EVENT_WINDOW_FRAMES)

        self.events: dict[str, list[int]] = {
            EventType.GOAL.value: [],
            EventType.CORNER.value: [],
            EventType.FREEKICK.value: [],
        }
        self._refractory_until: dict[str, int] = {
            EventType.GOAL.value: 0,
            EventType.CORNER.value: 0,
            EventType.FREEKICK.value: 0,
        }

    def push(self, frame_idx: int, ball_xy: tuple[float, float] | None) -> None:
        if ball_xy is None:
            return
        self.window.append(_BallSample(frame_idx, ball_xy[0], ball_xy[1]))
        self._check_goal(frame_idx)
        self._check_corner(frame_idx)
        self._check_freekick(frame_idx)

    def snapshot(self) -> dict[str, list[int]]:
        return {k: list(v) for k, v in self.events.items()}

    def _record(self, event: EventType, frame_idx: int, refractory: int) -> None:
        self.events[event.value].append(frame_idx)
        self._refractory_until[event.value] = frame_idx + refractory
        logger.info(f"[event] {event.value.upper()} detected at frame {frame_idx}")

    def _check_goal(self, frame_idx: int) -> None:
        if frame_idx < self._refractory_until[EventType.GOAL.value]:
            return
        hits = sum(
            1
            for p in self.window
            if self.goal_x_min <= p.x <= self.goal_x_max
            and (p.y < -self.outside_band or p.y > self.h + self.outside_band)
        )
        if hits >= config.EVENT_MIN_HITS:
            self._record(EventType.GOAL, frame_idx, config.GOAL_REFRACTORY_FRAMES)

    def _check_corner(self, frame_idx: int) -> None:
        if frame_idx < self._refractory_until[EventType.CORNER.value]:
            return
        for cx, cy in self.corners:
            hits = sum(
                1
                for p in self.window
                if (p.x - cx) ** 2 + (p.y - cy) ** 2 <= self.corner_radius_sq
            )
            if hits >= config.EVENT_MIN_HITS:
                self._record(
                    EventType.CORNER, frame_idx, config.CORNER_REFRACTORY_FRAMES,
                )
                return

    def _check_freekick(self, frame_idx: int) -> None:
        if frame_idx < self._refractory_until[EventType.FREEKICK.value]:
            return
        recent = list(self.window)[-config.FREEKICK_STATIONARY_FRAMES :]
        if len(recent) < config.FREEKICK_STATIONARY_FRAMES:
            return

        xs = [p.x for p in recent]
        ys = [p.y for p in recent]
        threshold = config.BALL_STATIONARY_VEL_FRACTION * max(self.w, self.h)
        if (max(xs) - min(xs)) > threshold or (max(ys) - min(ys)) > threshold:
            return

        # Don't double-fire with corner/goal — those have their own rules.
        x_last, y_last = recent[-1].x, recent[-1].y
        if self.goal_x_min <= x_last <= self.goal_x_max and (
            y_last < -self.outside_band or y_last > self.h + self.outside_band
        ):
            return
        if any(
            (x_last - cx) ** 2 + (y_last - cy) ** 2 <= self.corner_radius_sq
            for cx, cy in self.corners
        ):
            return

        self._record(EventType.FREEKICK, frame_idx, config.FREEKICK_REFRACTORY_FRAMES)
