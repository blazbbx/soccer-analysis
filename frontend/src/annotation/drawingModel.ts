import type {
  AnchoredDrawing,
  DrawingTool,
  DrawingView,
  StaticDrawing,
} from '../types/drawings';
import { TRACKING_GAP_TOLERANCE } from '../types/drawings';
import type { TrackingStore } from '../player/trackingStore';
import type { Point } from '../utils/canvasDrawing';
import type { ViewGeometry } from './viewGeometry';

const MIN_SHAPE_LENGTH = 3;

// Egy kattintás vagy elhanyagolható húzás nem rajz.
export function isMeaningfulStroke(tool: DrawingTool, points: readonly Point[]): boolean {
  if (points.length < 2) return false;
  if (tool === 'pen') return true;
  const first = points[0];
  const last = points[points.length - 1];
  return Math.hypot(last.x - first.x, last.y - first.y) >= MIN_SHAPE_LENGTH;
}

interface StrokeInput {
  id: string;
  view: DrawingView;
  tool: DrawingTool;
  color: string;
  points: readonly Point[];
  geometry: ViewGeometry;
}

export function buildStaticDrawing({ id, view, tool, color, points, geometry }: StrokeInput): StaticDrawing {
  return {
    id,
    type: 'static',
    view,
    tool,
    color,
    points: points.map((p) => ({ x: p.x / geometry.width, y: p.y / geometry.height })),
  };
}

export function buildAnchoredDrawing(
  input: StrokeInput & { playerId: number; anchorFrame: number; anchor: Point },
): AnchoredDrawing {
  const { id, view, tool, color, points, geometry, playerId, anchorFrame, anchor } = input;
  return {
    id,
    type: 'anchored',
    view,
    tool,
    color,
    playerId,
    anchorFrame,
    points: points.map((p) => ({
      dx: (p.x - anchor.x) / geometry.width,
      dy: (p.y - anchor.y) / geometry.height,
    })),
  };
}

export function resolveStaticPoints(drawing: StaticDrawing, geometry: ViewGeometry): Point[] {
  return drawing.points.map((p) => ({ x: p.x * geometry.width, y: p.y * geometry.height }));
}

export interface ResolvedAnchored {
  points: Point[];
  // 0 = a játékos a megjelenített képkockán látszik; > 0 = csak a közelben, ezért halványítva rajzoljuk.
  gap: number;
}

export function resolveAnchoredPoints(
  drawing: AnchoredDrawing,
  geometry: ViewGeometry,
  tracking: TrackingStore,
  frame: number,
): ResolvedAnchored | null {
  const found = tracking.findPlayer(frame, drawing.playerId, TRACKING_GAP_TOLERANCE);
  if (!found) return null;
  const anchor = geometry.anchorOf(found.entry);
  if (!anchor) return null;

  return {
    gap: found.gap,
    points: drawing.points.map((p) => ({
      x: anchor.x + p.dx * geometry.width,
      y: anchor.y + p.dy * geometry.height,
    })),
  };
}
