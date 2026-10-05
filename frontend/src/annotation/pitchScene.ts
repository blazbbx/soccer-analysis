import {
  BALL_RADIUS,
  CENTER_R,
  CORNER_R,
  GOAL_DEPTH,
  GOAL_POST_DEPTH,
  GOAL_POST_WIDTH,
  GOAL_WIDTH,
  PENALTY_DEPTH,
  PENALTY_SPOT,
  PENALTY_WIDTH,
  PITCH_H,
  PITCH_VIEW,
  PITCH_W,
  PLAYER_RADIUS,
  PX_PER_METER,
} from '../constants/pitch';
import type { FrameSnapshot } from '../player/frameSnapshot';
import type { TrackingEntry } from '../types/trackingData';

export interface TeamColors {
  home?: string;
  away?: string;
  referee?: string;
}

// Csak akkor használjuk, ha egy bejegyzésnek nincs `team` mezője (pl. mock kimenet).
const FALLBACK_PLAYER_COLORS = [
  '#e74c3c', '#3498db', '#2ecc71', '#f39c12', '#9b59b6',
  '#1abc9c', '#e67e22', '#34495e', '#e91e63', '#00bcd4',
  '#ff5722', '#8bc34a',
];

const X = (metre: number) => (metre - PITCH_VIEW.x) * PX_PER_METER;
const Y = (metre: number) => (metre - PITCH_VIEW.y) * PX_PER_METER;
const S = (metre: number) => metre * PX_PER_METER;

function colorFor(entry: TrackingEntry, teamColors?: TeamColors): string {
  if (entry.team && teamColors?.[entry.team]) return teamColors[entry.team]!;
  return FALLBACK_PLAYER_COLORS[(entry.player_id - 1) % FALLBACK_PLAYER_COLORS.length];
}

// A pálya állandó háttere (csíkok, vonalak). Egyszer rajzoljuk egy külön canvasra, és képkockánként másoljuk.
export function renderPitchBackground(ctx: CanvasRenderingContext2D): void {
  ctx.fillStyle = '#1a6b1a';
  ctx.fillRect(0, 0, S(PITCH_VIEW.w), S(PITCH_VIEW.h));

  const stripeW = PITCH_W / 10;
  for (let i = 0; i < 10; i++) {
    ctx.fillStyle = i % 2 === 0 ? '#1e7a1e' : '#1a6b1a';
    ctx.fillRect(X(i * stripeW), Y(0), S(stripeW), S(PITCH_H));
  }

  const white = 'rgba(255,255,255,0.85)';
  ctx.strokeStyle = white;
  ctx.fillStyle = white;
  ctx.lineWidth = S(0.4);

  const midX = PITCH_W / 2;
  const midY = PITCH_H / 2;
  const penaltyY = (PITCH_H - PENALTY_WIDTH) / 2;
  const goalAreaY = (PITCH_H - GOAL_WIDTH) / 2;
  const goalPostY = (PITCH_H - GOAL_POST_WIDTH) / 2;

  ctx.strokeRect(X(0), Y(0), S(PITCH_W), S(PITCH_H));

  ctx.beginPath();
  ctx.moveTo(X(midX), Y(0));
  ctx.lineTo(X(midX), Y(PITCH_H));
  ctx.stroke();

  ctx.beginPath();
  ctx.arc(X(midX), Y(midY), S(CENTER_R), 0, Math.PI * 2);
  ctx.stroke();
  ctx.beginPath();
  ctx.arc(X(midX), Y(midY), S(0.5), 0, Math.PI * 2);
  ctx.fill();

  ctx.strokeRect(X(0), Y(penaltyY), S(PENALTY_DEPTH), S(PENALTY_WIDTH));
  ctx.strokeRect(X(0), Y(goalAreaY), S(GOAL_DEPTH), S(GOAL_WIDTH));
  ctx.beginPath();
  ctx.arc(X(PENALTY_SPOT), Y(midY), S(0.4), 0, Math.PI * 2);
  ctx.fill();

  ctx.strokeRect(X(PITCH_W - PENALTY_DEPTH), Y(penaltyY), S(PENALTY_DEPTH), S(PENALTY_WIDTH));
  ctx.strokeRect(X(PITCH_W - GOAL_DEPTH), Y(goalAreaY), S(GOAL_DEPTH), S(GOAL_WIDTH));
  ctx.beginPath();
  ctx.arc(X(PITCH_W - PENALTY_SPOT), Y(midY), S(0.4), 0, Math.PI * 2);
  ctx.fill();

  ctx.lineWidth = S(0.5);
  ctx.strokeRect(X(-GOAL_POST_DEPTH), Y(goalPostY), S(GOAL_POST_DEPTH), S(GOAL_POST_WIDTH));
  ctx.strokeRect(X(PITCH_W), Y(goalPostY), S(GOAL_POST_DEPTH), S(GOAL_POST_WIDTH));

  ctx.lineWidth = S(0.4);
  const corner = S(CORNER_R);
  ctx.beginPath();
  ctx.arc(X(0), Y(0), corner, 0, Math.PI / 2);
  ctx.stroke();
  ctx.beginPath();
  ctx.arc(X(PITCH_W), Y(0), corner, Math.PI, Math.PI / 2, true);
  ctx.stroke();
  ctx.beginPath();
  ctx.arc(X(0), Y(PITCH_H), corner, -Math.PI / 2, 0);
  ctx.stroke();
  ctx.beginPath();
  ctx.arc(X(PITCH_W), Y(PITCH_H), corner, -Math.PI / 2, Math.PI, true);
  ctx.stroke();
}

// A képkockánként változó rész: játékosok és labda.
export function renderPitchActors(
  ctx: CanvasRenderingContext2D,
  snapshot: FrameSnapshot,
  teamColors?: TeamColors,
): void {
  const playerR = S(PLAYER_RADIUS);
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.font = `bold ${Math.max(8, Math.round(playerR))}px sans-serif`;

  for (const entry of snapshot.players) {
    if (entry.tx == null || entry.ty == null) continue;
    const x = X(entry.tx);
    const y = Y(entry.ty);

    ctx.beginPath();
    ctx.arc(x, y, playerR, 0, Math.PI * 2);
    ctx.fillStyle = colorFor(entry, teamColors);
    ctx.fill();
    ctx.strokeStyle = 'white';
    ctx.lineWidth = S(0.3);
    ctx.stroke();

    ctx.fillStyle = 'white';
    ctx.fillText(String(entry.player_id), x, y);
  }

  const ball = snapshot.ball;
  if (ball && ball.tx != null && ball.ty != null) {
    ctx.beginPath();
    ctx.arc(X(ball.tx), Y(ball.ty), S(BALL_RADIUS), 0, Math.PI * 2);
    ctx.fillStyle = '#000000';
    ctx.fill();
    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = S(0.25);
    ctx.stroke();
  }
}
