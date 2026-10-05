import { PITCH_CANVAS, PITCH_VIEW, PLAYER_RADIUS, PX_PER_METER } from '../constants/pitch';
import { VIDEO_VIEW } from '../constants/video';
import type { TrackingEntry } from '../types/trackingData';
import type { Point } from '../utils/canvasDrawing';

export interface MarkerStyle {
  color: string;
  label?: string;
  dashed?: boolean;
}

// A nézet geometriája: hogyan képződik le egy játékos a nézet vásznára, hol találja el egy kattintás,
// és hogyan rajzolódik a jelölője. Az Annotation réteg ezen keresztül kezeli mindkét nézetet.
export interface ViewGeometry {
  readonly width: number;
  readonly height: number;
  anchorOf(player: TrackingEntry): Point | null;
  contains(player: TrackingEntry, x: number, y: number): boolean;
  area(player: TrackingEntry): number;
  drawMarker(ctx: CanvasRenderingContext2D, player: TrackingEntry, style: MarkerStyle): void;
}

export function pitchToCanvas(tx: number, ty: number): Point {
  return {
    x: (tx - PITCH_VIEW.x) * PX_PER_METER,
    y: (ty - PITCH_VIEW.y) * PX_PER_METER,
  };
}

export const VIDEO_GEOMETRY: ViewGeometry = {
  width: VIDEO_VIEW.width,
  height: VIDEO_VIEW.height,
  anchorOf: (p) => ({ x: (p.x1 + p.x2) / 2, y: (p.y1 + p.y2) / 2 }),
  contains: (p, x, y) => x >= p.x1 && x <= p.x2 && y >= p.y1 && y <= p.y2,
  area: (p) => (p.x2 - p.x1) * (p.y2 - p.y1),
  drawMarker(ctx, p, { color, label, dashed }) {
    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = 2;
    if (dashed) ctx.setLineDash([4, 3]);
    ctx.strokeRect(p.x1, p.y1, p.x2 - p.x1, p.y2 - p.y1);
    if (label) {
      ctx.fillStyle = color;
      ctx.font = 'bold 12px sans-serif';
      ctx.fillText(label, p.x1 + 3, p.y1 + 14);
    }
    ctx.restore();
  },
};

const PLAYER_PX = PLAYER_RADIUS * PX_PER_METER;
const PITCH_HIT_RADIUS = PLAYER_PX * 2;

export const PITCH_GEOMETRY: ViewGeometry = {
  width: PITCH_CANVAS.width,
  height: PITCH_CANVAS.height,
  anchorOf: (p) => (p.tx == null || p.ty == null ? null : pitchToCanvas(p.tx, p.ty)),
  contains(p, x, y) {
    const anchor = this.anchorOf(p);
    return anchor !== null && Math.hypot(x - anchor.x, y - anchor.y) <= PITCH_HIT_RADIUS;
  },
  area: () => Math.PI * PITCH_HIT_RADIUS ** 2,
  drawMarker(ctx, p, { color, label, dashed }) {
    const anchor = this.anchorOf(p);
    if (!anchor) return;
    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = dashed ? 2 : 2.5;
    if (dashed) ctx.setLineDash([4, 3]);
    ctx.beginPath();
    ctx.arc(anchor.x, anchor.y, PLAYER_PX * (dashed ? 2.2 : 1.6), 0, Math.PI * 2);
    ctx.stroke();
    if (label) {
      ctx.fillStyle = color;
      ctx.font = `bold ${Math.max(10, PLAYER_PX * 1.2)}px sans-serif`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'bottom';
      ctx.fillText(label, anchor.x, anchor.y - PLAYER_PX * 1.8);
    }
    ctx.restore();
  },
};

// Átfedő jelölőknél a kisebb területű nyer (D7); egyenlő területnél a középponthoz közelebbi.
export function pickPlayer(
  players: readonly TrackingEntry[],
  geometry: ViewGeometry,
  x: number,
  y: number,
): TrackingEntry | null {
  let best: TrackingEntry | null = null;
  let bestArea = Infinity;
  let bestDistance = Infinity;

  for (const player of players) {
    if (!geometry.contains(player, x, y)) continue;
    const anchor = geometry.anchorOf(player);
    const distance = anchor ? Math.hypot(x - anchor.x, y - anchor.y) : Infinity;
    const area = geometry.area(player);
    if (area < bestArea || (area === bestArea && distance < bestDistance)) {
      best = player;
      bestArea = area;
      bestDistance = distance;
    }
  }
  return best;
}
