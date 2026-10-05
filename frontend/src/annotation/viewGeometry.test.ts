import { describe, expect, it } from 'vitest';
import { PITCH_VIEW, PLAYER_RADIUS, PX_PER_METER } from '../constants/pitch';
import type { TrackingEntry } from '../types/trackingData';
import { PITCH_GEOMETRY, VIDEO_GEOMETRY, pickPlayer, pitchToCanvas } from './viewGeometry';

const box = (id: number, x1: number, y1: number, x2: number, y2: number): TrackingEntry => ({
  frame: 0,
  player_id: id,
  x1,
  y1,
  x2,
  y2,
});

const onPitch = (id: number, tx: number, ty: number): TrackingEntry => ({ ...box(id, 0, 0, 1, 1), tx, ty });

describe('videó-nézet geometria', () => {
  it('a horgony a doboz közepe', () => {
    expect(VIDEO_GEOMETRY.anchorOf(box(1, 100, 200, 140, 280))).toEqual({ x: 120, y: 240 });
  });

  it('a találat a dobozon belül igaz, kívül hamis', () => {
    const p = box(1, 100, 200, 140, 280);
    expect(VIDEO_GEOMETRY.contains(p, 120, 240)).toBe(true);
    expect(VIDEO_GEOMETRY.contains(p, 99, 240)).toBe(false);
  });
});

describe('pálya-nézet geometria', () => {
  it('a pálya origója a margó utáni pixelre esik, 12 px/méter', () => {
    expect(pitchToCanvas(0, 0)).toEqual({
      x: -PITCH_VIEW.x * PX_PER_METER,
      y: -PITCH_VIEW.y * PX_PER_METER,
    });
    expect(pitchToCanvas(10, 0).x - pitchToCanvas(0, 0).x).toBe(10 * PX_PER_METER);
  });

  it('a pálya tx/ty nélküli játékost nem horgonyoz', () => {
    expect(PITCH_GEOMETRY.anchorOf(box(1, 0, 0, 1, 1))).toBeNull();
  });

  it('a találat a jelölő sugarán belül igaz', () => {
    const p = onPitch(1, 50, 30);
    const c = pitchToCanvas(50, 30);
    expect(PITCH_GEOMETRY.contains(p, c.x + PLAYER_RADIUS * PX_PER_METER, c.y)).toBe(true);
    expect(PITCH_GEOMETRY.contains(p, c.x + 200, c.y)).toBe(false);
  });
});

describe('pickPlayer', () => {
  it('átfedő dobozoknál a kisebb területű nyer (D7)', () => {
    const large = box(1, 100, 100, 200, 300);
    const small = box(2, 130, 150, 160, 220);
    expect(pickPlayer([large, small], VIDEO_GEOMETRY, 140, 180)?.player_id).toBe(2);
    expect(pickPlayer([small, large], VIDEO_GEOMETRY, 140, 180)?.player_id).toBe(2);
  });

  it('csak azt találja el, amelyik tartalmazza a pontot', () => {
    const a = box(1, 0, 0, 10, 10);
    const b = box(2, 100, 100, 110, 110);
    expect(pickPlayer([a, b], VIDEO_GEOMETRY, 105, 105)?.player_id).toBe(2);
    expect(pickPlayer([a, b], VIDEO_GEOMETRY, 50, 50)).toBeNull();
  });

  it('a pályán egyenlő területnél a középponthoz közelebbi nyer', () => {
    const near = onPitch(1, 50, 30);
    const far = onPitch(2, 51.5, 30);
    const c = pitchToCanvas(50.2, 30);
    expect(pickPlayer([far, near], PITCH_GEOMETRY, c.x, c.y)?.player_id).toBe(1);
  });

  it('üres listánál null', () => {
    expect(pickPlayer([], VIDEO_GEOMETRY, 5, 5)).toBeNull();
  });
});
