import type { FrameSnapshot } from '../player/frameSnapshot';
import type { TrackingStore } from '../player/trackingStore';
import type { ClipDrawing, DrawingTool, DrawingView } from '../types/drawings';
import { TRACKING_GAP_TOLERANCE } from '../types/drawings';
import { drawArrow, drawCircle, drawPenPath, type Point } from '../utils/canvasDrawing';
import { inputModeOf, type AnnotationState } from './annotationStore';
import { resolveAnchoredPoints, resolveStaticPoints } from './drawingModel';
import type { ViewGeometry } from './viewGeometry';

export const MARKER_COLORS = [
  '#f44336', '#ff9800', '#ffeb3b', '#4caf50',
  '#2196f3', '#9c27b0', '#00bcd4', '#ff5722',
];

export interface StrokePreview {
  tool: DrawingTool;
  color: string;
  points: readonly Point[];
}

export function drawStroke(
  ctx: CanvasRenderingContext2D,
  tool: DrawingTool,
  color: string,
  points: readonly Point[],
): void {
  if (tool === 'pen') drawPenPath(ctx, [...points], color);
  else if (tool === 'arrow' && points.length >= 2) drawArrow(ctx, points[0], points[points.length - 1], color);
  else if (tool === 'circle' && points.length >= 2) drawCircle(ctx, points[0], points[1], color);
}

// A rajzok rétege (content): ez kerül a felvételbe.
export function renderContent(
  ctx: CanvasRenderingContext2D,
  args: {
    drawings: readonly ClipDrawing[];
    view: DrawingView;
    geometry: ViewGeometry;
    tracking: TrackingStore;
    frame: number;
  },
): void {
  const { drawings, view, geometry, tracking, frame } = args;
  ctx.clearRect(0, 0, geometry.width, geometry.height);

  for (const drawing of drawings) {
    if (drawing.view !== view) continue;

    if (drawing.type === 'static') {
      drawStroke(ctx, drawing.tool, drawing.color, resolveStaticPoints(drawing, geometry));
      continue;
    }

    const resolved = resolveAnchoredPoints(drawing, geometry, tracking, frame);
    if (!resolved) continue;
    ctx.globalAlpha = resolved.gap > 0 ? 0.4 : 1;
    drawStroke(ctx, drawing.tool, drawing.color, resolved.points);
    ctx.globalAlpha = 1;
  }
}

// A kezelőfelületi elemek rétege (chrome): jelölők, kiemelés, húzás közbeni előnézet. Sosem kerül a felvételbe.
export function renderChrome(
  ctx: CanvasRenderingContext2D,
  args: {
    state: AnnotationState;
    geometry: ViewGeometry;
    snapshot: FrameSnapshot;
    tracking: TrackingStore;
    preview: StrokePreview | null;
  },
): void {
  const { state, geometry, snapshot, tracking, preview } = args;
  ctx.clearRect(0, 0, geometry.width, geometry.height);

  if (inputModeOf(state) === 'select') {
    snapshot.players.forEach((player, index) => {
      geometry.drawMarker(ctx, player, {
        color: MARKER_COLORS[index % MARKER_COLORS.length],
        label: `#${player.player_id}`,
      });
    });
  }

  if (state.followMode && state.selectedPlayerId !== null) {
    const found = tracking.findPlayer(snapshot.frame, state.selectedPlayerId, TRACKING_GAP_TOLERANCE);
    if (found) geometry.drawMarker(ctx, found.entry, { color: '#ffffff', dashed: true });
  }

  if (preview) drawStroke(ctx, preview.tool, preview.color, preview.points);
}
