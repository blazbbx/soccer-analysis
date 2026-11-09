import numpy as np

# Rows of the frame the median is computed for at a time (a memory bound only; the result does not depend on it).
MEDIAN_BAND_ROWS = 64


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

    # The median is taken independently per pixel and channel, so it can be computed band by band
    # with exactly the same result as over the whole stack. Stacking ALL frames first doubled the
    # memory of the sampled frames and np.median then made one more partitioned copy: for 90 frames
    # of 2560x1440 that was ~3 GB at the peak, and the worker was OOM-killed (exit 137) in the 8 GB
    # Docker VM. A band keeps the temporary memory at about len(frames) * band rows * width * 3 bytes.
    result = np.empty(frames[0].shape, dtype=np.uint8)
    height = frames[0].shape[0]
    for top in range(0, height, MEDIAN_BAND_ROWS):
        bottom = min(top + MEDIAN_BAND_ROWS, height)
        band = np.stack([frame[top:bottom] for frame in frames], axis=0)
        result[top:bottom] = np.median(band, axis=0).astype(np.uint8)
    return result
