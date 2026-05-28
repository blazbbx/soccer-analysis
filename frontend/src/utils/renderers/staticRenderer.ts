import type { StaticDrawing } from '../../types/drawings';
import { drawPenPath, drawArrow, drawCircle } from '../canvasDrawing';

export function renderStaticDrawings(
  ctx: CanvasRenderingContext2D,
  drawings: StaticDrawing[],
  canvasWidth: number,
  canvasHeight: number
): void {
  for (const drawing of drawings) {
    const pixelPoints = drawing.points.map((p) => ({
      x: p.x * canvasWidth,
      y: p.y * canvasHeight,
    }));

    if (drawing.tool === 'pen') {
      drawPenPath(ctx, pixelPoints, drawing.color);
    } else if (drawing.tool === 'arrow' && pixelPoints.length >= 2) {
      drawArrow(ctx, pixelPoints[0], pixelPoints[pixelPoints.length - 1], drawing.color);
    } else if (drawing.tool === 'circle' && pixelPoints.length >= 2) {
      drawCircle(ctx, pixelPoints[0], pixelPoints[1], drawing.color);
    }
  }
}
