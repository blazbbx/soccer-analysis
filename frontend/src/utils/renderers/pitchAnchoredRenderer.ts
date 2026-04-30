import type { AnchoredDrawing } from '../../types/drawings';
import { TRACKING_GAP_TOLERANCE } from '../../types/drawings';
import type { TrackingFrameMap } from '../../pages/matches/hooks/VideoEdit/useTrackingData';
import { drawPenPath, drawArrow, drawCircle } from '../canvasDrawing';
import { findPlayerEntry } from './anchoredRenderer';

// SVG viewBox: `-3 -2 ${PITCH_W+6} ${PITCH_H+4}` = -3 -2 111 72
const VB_X = -3;
const VB_Y = -2;
const VB_W = 111;
const VB_H = 72;

export function pitchToCanvas(
  tx: number,
  ty: number,
  canvasWidth: number,
  canvasHeight: number,
): { x: number; y: number } {
  return {
    x: (tx - VB_X) / VB_W * canvasWidth,
    y: (ty - VB_Y) / VB_H * canvasHeight,
  };
}

export function renderPitchAnchoredDrawings(
  ctx: CanvasRenderingContext2D,
  drawings: AnchoredDrawing[],
  canvasWidth: number,
  canvasHeight: number,
  currentFrame: number,
  frameMap: TrackingFrameMap,
): void {
  for (const drawing of drawings) {
    const result = findPlayerEntry(frameMap, currentFrame, drawing.playerId, TRACKING_GAP_TOLERANCE);
    if (!result) continue;

    const { entry, gap } = result;
    if (entry.tx == null || entry.ty == null) continue;

    const { x: cx, y: cy } = pitchToCanvas(entry.tx, entry.ty, canvasWidth, canvasHeight);

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
