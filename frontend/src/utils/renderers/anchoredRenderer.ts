import type { AnchoredDrawing } from '../../types/drawings';
import { TRACKING_GAP_TOLERANCE } from '../../types/drawings';
import type { TrackingFrameMap } from '../../hooks/VideoEdit/useTrackingData';
import type { TrackingEntry } from '../../types/trackingData';
import { drawPenPath, drawArrow, drawCircle } from '../canvasDrawing';

export function findPlayerEntry(
  frameMap: TrackingFrameMap,
  targetFrame: number,
  playerId: number,
  tolerance: number
): { entry: TrackingEntry; gap: number } | null {
  for (let gap = 0; gap <= tolerance; gap++) {
    const fwd = frameMap.get(targetFrame + gap)?.find((e) => e.player_id === playerId);
    if (fwd) return { entry: fwd, gap };
    if (gap > 0) {
      const bwdFrame = targetFrame - gap;
      if (bwdFrame >= 0) {
        const bwd = frameMap.get(bwdFrame)?.find((e) => e.player_id === playerId);
        if (bwd) return { entry: bwd, gap };
      }
    }
  }
  return null;
}

export function renderAnchoredDrawings(
  ctx: CanvasRenderingContext2D,
  drawings: AnchoredDrawing[],
  canvasWidth: number,
  canvasHeight: number,
  currentFrame: number,
  frameMap: TrackingFrameMap,
  scaleX: number,
  scaleY: number
): void {
  for (const drawing of drawings) {
    const result = findPlayerEntry(frameMap, currentFrame, drawing.playerId, TRACKING_GAP_TOLERANCE);
    if (!result) continue;

    const { entry, gap } = result;
    const cx = ((entry.x1 + entry.x2) / 2) * scaleX;
    const cy = ((entry.y1 + entry.y2) / 2) * scaleY;

    const pixelPoints = drawing.points.map((p) => ({
      x: cx + p.dx * canvasWidth,
      y: cy + p.dy * canvasHeight,
    }));

    ctx.globalAlpha = gap > 0 ? 0.4 : 1.0;

    if (drawing.tool === 'pen') {
      drawPenPath(ctx, pixelPoints, drawing.color);
    } else if (drawing.tool === 'arrow' && pixelPoints.length >= 2) {
      drawArrow(ctx, pixelPoints[0], pixelPoints[pixelPoints.length - 1], drawing.color);
    } else if (drawing.tool === 'circle' && pixelPoints.length >= 2) {
      drawCircle(ctx, pixelPoints[0], pixelPoints[1], drawing.color);
    }

    ctx.globalAlpha = 1.0;
  }
}
