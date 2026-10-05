import numpy as np


def sample_stride(remaining_frames: int, sample_count: int) -> int:
    """How many frames to skip between samples so `sample_count` samples
    spread evenly over `remaining_frames`. Always >= 1.
    """
    return max(1, remaining_frames // max(sample_count, 1))


def compute_median_image(frames: list[np.ndarray]) -> np.ndarray:
    """Per-pixel, per-channel median across the sampled frames.

    As long as no single pixel location is covered by a moving object
    (player/ball) in more than half of the samples, the median recovers the
    static background at that pixel — unlike a mean, a minority of
    contaminated samples has zero influence on the result (see
    docs/ai-collaboration-log.md, 2026-09-27, for the full reasoning and its
    one known failure mode: a player standing still for most of the window).
    """
    if not frames:
        msg = "compute_median_image requires at least one frame"
        raise ValueError(msg)

    stack = np.stack(frames, axis=0)
    return np.median(stack, axis=0).astype(np.uint8)
