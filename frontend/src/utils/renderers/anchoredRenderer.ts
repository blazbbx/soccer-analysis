import type { AnchoredDrawing } from '../../types/drawings';
import { TRACKING_GAP_TOLERANCE } from '../../types/drawings';
import type { TrackingFrameMap } from '../../hooks/VideoEdit/useTrackingData';
import type { TrackingEntry } from '../../types/trackingData';
import { drawPenPath, drawArrow, drawCircle } from '../canvasDrawing';

const pointPool: { x: number; y: number }[] = [];

export function findPlayerEntry(
  frameMap: TrackingFrameMap,
  targetFrame: number,
  playerId: number,
  tolerance: number
): { entry: TrackingEntry; gap: number } | null {
  const exactFrame = frameMap.get(targetFrame);
  if (exactFrame) {
    for (let i = 0; i < exactFrame.length; i++) {
      if (exactFrame[i].player_id === playerId) {
        return { entry: exactFrame[i], gap: 0 };
      }
    }
  }

  for (let gap = 1; gap <= tolerance; gap++) {
    const fwdFrame = frameMap.get(targetFrame + gap);
    if (fwdFrame) {
      for (let i = 0; i < fwdFrame.length; i++) {
        if (fwdFrame[i].player_id === playerId) {
          return { entry: fwdFrame[i], gap };
        }
      }
    }

    const bwdFrameIndex = targetFrame - gap;
    if (bwdFrameIndex >= 0) {
      const bwdFrame = frameMap.get(bwdFrameIndex);
      if (bwdFrame) {
        for (let i = 0; i < bwdFrame.length; i++) {
          if (bwdFrame[i].player_id === playerId) {
            return { entry: bwdFrame[i], gap };
          }
        }
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
  for (let i = 0; i < drawings.length; i++) {
    const drawing = drawings[i];
    const result = findPlayerEntry(frameMap, currentFrame, drawing.playerId, TRACKING_GAP_TOLERANCE);
    
    if (!result) continue;

    const { entry, gap } = result;
    const cx = ((entry.x1 + entry.x2) / 2) * scaleX;
    const cy = ((entry.y1 + entry.y2) / 2) * scaleY;
    const pointsLen = drawing.points.length;

    if (pointPool.length < pointsLen) {
      const toAdd = pointsLen - pointPool.length;
      for (let j = 0; j < toAdd; j++) {
        pointPool.push({ x: 0, y: 0 });
      }
    }

    for (let j = 0; j < pointsLen; j++) {
      const p = drawing.points[j];
      pointPool[j].x = cx + p.dx * canvasWidth;
      pointPool[j].y = cy + p.dy * canvasHeight;
    }

    const activePoints = pointPool.slice(0, pointsLen);

    ctx.globalAlpha = gap > 0 ? 0.4 : 1.0;

    if (drawing.tool === 'pen') {
      drawPenPath(ctx, activePoints, drawing.color);
    } else if (drawing.tool === 'arrow' && pointsLen >= 2) {
      drawArrow(ctx, activePoints[0], activePoints[pointsLen - 1], drawing.color);
    } else if (drawing.tool === 'circle' && pointsLen >= 2) {
      drawCircle(ctx, activePoints[0], activePoints[1], drawing.color);
    }

    ctx.globalAlpha = 1.0;
  }
}