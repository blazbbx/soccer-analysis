import type { TrackingEntry } from '../../types/trackingData';

// Pitch dimensions in metres
const PITCH_W = 105;
const PITCH_H = 68;

// SVG viewBox: -3 -2 111 72 (3m margin on each side, 2m top/bottom)
const VB_X = -3;
const VB_Y = -2;
const VB_W = 111;
const VB_H = 72;

const PENALTY_DEPTH  = 16.5;
const PENALTY_WIDTH  = 40.32;
const GOAL_DEPTH     = 5.5;
const GOAL_WIDTH     = 18.32;
const GOAL_POST_DEPTH = 2.44;
const GOAL_POST_WIDTH = 7.32;
const CENTER_R       = 9.15;
const PENALTY_SPOT   = 11;
const CORNER_R       = 1;
const PLAYER_RADIUS  = 1.6;

const PLAYER_COLORS = [
  '#e74c3c', '#3498db', '#2ecc71', '#f39c12', '#9b59b6',
  '#1abc9c', '#e67e22', '#34495e', '#e91e63', '#00bcd4',
  '#ff5722', '#8bc34a',
];

// Pitch metres → canvas pixels
const px = (v: number, w: number) => (v - VB_X) / VB_W * w;
const py = (v: number, h: number) => (v - VB_Y) / VB_H * h;
const pw = (v: number, w: number) => v / VB_W * w;
const ph = (v: number, h: number) => v / VB_H * h;
// Radius uses width scale (aspect ratio of canvas matches viewBox)
const pr = (v: number, w: number) => v / VB_W * w;

export function renderPitchFrame(
  ctx: CanvasRenderingContext2D,
  canvasWidth: number,
  canvasHeight: number,
  entries: TrackingEntry[]
): void {
  const W = canvasWidth;
  const H = canvasHeight;

  // Background (covers viewBox margins too)
  ctx.fillStyle = '#1a6b1a';
  ctx.fillRect(0, 0, W, H);

  // 10 alternating stripes over the pitch area
  const stripeW = PITCH_W / 10;
  for (let i = 0; i < 10; i++) {
    ctx.fillStyle = i % 2 === 0 ? '#1e7a1e' : '#1a6b1a';
    ctx.fillRect(px(i * stripeW, W), py(0, H), pw(stripeW, W), ph(PITCH_H, H));
  }

  // Field markings
  const white = 'rgba(255,255,255,0.85)';
  ctx.strokeStyle = white;
  ctx.fillStyle   = white;
  ctx.lineWidth   = pw(0.4, W);

  const midX      = PITCH_W / 2;
  const midY      = PITCH_H / 2;
  const penaltyY  = (PITCH_H - PENALTY_WIDTH)  / 2;
  const goalAreaY = (PITCH_H - GOAL_WIDTH)      / 2;
  const goalPostY = (PITCH_H - GOAL_POST_WIDTH) / 2;

  // Pitch boundary
  ctx.strokeRect(px(0, W), py(0, H), pw(PITCH_W, W), ph(PITCH_H, H));

  // Center line
  ctx.beginPath();
  ctx.moveTo(px(midX, W), py(0, H));
  ctx.lineTo(px(midX, W), py(PITCH_H, H));
  ctx.stroke();

  // Center circle + spot
  ctx.beginPath();
  ctx.arc(px(midX, W), py(midY, H), pr(CENTER_R, W), 0, Math.PI * 2);
  ctx.stroke();
  ctx.beginPath();
  ctx.arc(px(midX, W), py(midY, H), pr(0.5, W), 0, Math.PI * 2);
  ctx.fill();

  // Left penalty area + goal area + spot
  ctx.strokeRect(px(0, W), py(penaltyY, H), pw(PENALTY_DEPTH, W), ph(PENALTY_WIDTH, H));
  ctx.strokeRect(px(0, W), py(goalAreaY, H), pw(GOAL_DEPTH, W),   ph(GOAL_WIDTH, H));
  ctx.beginPath();
  ctx.arc(px(PENALTY_SPOT, W), py(midY, H), pr(0.4, W), 0, Math.PI * 2);
  ctx.fill();

  // Right penalty area + goal area + spot
  ctx.strokeRect(px(PITCH_W - PENALTY_DEPTH, W), py(penaltyY, H), pw(PENALTY_DEPTH, W), ph(PENALTY_WIDTH, H));
  ctx.strokeRect(px(PITCH_W - GOAL_DEPTH, W),    py(goalAreaY, H), pw(GOAL_DEPTH, W),   ph(GOAL_WIDTH, H));
  ctx.beginPath();
  ctx.arc(px(PITCH_W - PENALTY_SPOT, W), py(midY, H), pr(0.4, W), 0, Math.PI * 2);
  ctx.fill();

  // Goal posts (slightly thicker)
  ctx.lineWidth = pw(0.5, W);
  ctx.strokeRect(px(-GOAL_POST_DEPTH, W), py(goalPostY, H), pw(GOAL_POST_DEPTH, W), ph(GOAL_POST_WIDTH, H));
  ctx.strokeRect(px(PITCH_W, W),          py(goalPostY, H), pw(GOAL_POST_DEPTH, W), ph(GOAL_POST_WIDTH, H));

  // Corner arcs — quarter circles curving into the pitch from each corner
  ctx.lineWidth = pw(0.4, W);
  const cr = pr(CORNER_R, W);

  ctx.beginPath(); // top-left: right → down (CW)
  ctx.arc(px(0, W),       py(0, H),       cr, 0,           Math.PI / 2);
  ctx.stroke();

  ctx.beginPath(); // top-right: left → down (CCW)
  ctx.arc(px(PITCH_W, W), py(0, H),       cr, Math.PI,     Math.PI / 2, true);
  ctx.stroke();

  ctx.beginPath(); // bottom-left: up → right (CW)
  ctx.arc(px(0, W),       py(PITCH_H, H), cr, -Math.PI / 2, 0);
  ctx.stroke();

  ctx.beginPath(); // bottom-right: up → left (CCW)
  ctx.arc(px(PITCH_W, W), py(PITCH_H, H), cr, -Math.PI / 2, Math.PI, true);
  ctx.stroke();

  // Player dots
  const playerR  = pr(PLAYER_RADIUS, W);
  const fontSize = Math.max(8, Math.round(playerR));

  ctx.textAlign    = 'center';
  ctx.textBaseline = 'middle';
  ctx.font         = `bold ${fontSize}px sans-serif`;

  for (const entry of entries) {
    if (entry.tx == null || entry.ty == null) continue;
    const x     = px(entry.tx, W);
    const y     = py(entry.ty, H);
    const color = PLAYER_COLORS[(entry.player_id - 1) % PLAYER_COLORS.length];

    ctx.beginPath();
    ctx.arc(x, y, playerR, 0, Math.PI * 2);
    ctx.fillStyle = color;
    ctx.fill();

    ctx.strokeStyle = 'white';
    ctx.lineWidth   = pw(0.3, W);
    ctx.stroke();

    ctx.fillStyle = 'white';
    ctx.fillText(String(entry.player_id), x, y);
  }
}
