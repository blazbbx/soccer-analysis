import { describe, expect, it } from 'vitest';
import { createTrackingStore } from '../player/trackingStore';
import type { TrackingEntry } from '../types/trackingData';
import {
  buildAnchoredDrawing,
  buildStaticDrawing,
  isMeaningfulStroke,
  resolveAnchoredPoints,
  resolveStaticPoints,
} from './drawingModel';
import { VIDEO_GEOMETRY } from './viewGeometry';

const player = (frame: number, id: number, x1: number, y1: number, x2: number, y2: number): TrackingEntry => ({
  frame,
  player_id: id,
  x1,
  y1,
  x2,
  y2,
});

describe('isMeaningfulStroke', () => {
  it('egy pont (kattintás) nem rajz', () => {
    expect(isMeaningfulStroke('pen', [{ x: 1, y: 1 }])).toBe(false);
  });

  it('a toll két ponttól rajz, a nyíl és a kör csak elég hosszú húzásnál', () => {
    const near = [{ x: 0, y: 0 }, { x: 1, y: 0 }];
    const far = [{ x: 0, y: 0 }, { x: 30, y: 0 }];
    expect(isMeaningfulStroke('pen', near)).toBe(true);
    expect(isMeaningfulStroke('arrow', near)).toBe(false);
    expect(isMeaningfulStroke('circle', far)).toBe(true);
  });
});

describe('statikus rajz', () => {
  it('normalizál a nézet méretére, és visszaállítja ugyanazokat a pontokat', () => {
    const points = [{ x: 640, y: 360 }, { x: 1280, y: 720 }];
    const drawing = buildStaticDrawing({
      id: 's', view: 'video', tool: 'pen', color: '#fff', points, geometry: VIDEO_GEOMETRY,
    });
    expect(drawing.points).toEqual([{ x: 0.5, y: 0.5 }, { x: 1, y: 1 }]);
    expect(resolveStaticPoints(drawing, VIDEO_GEOMETRY)).toEqual(points);
  });
});

describe('követő rajz', () => {
  const tracking = createTrackingStore({
    videoFps: 50,
    trackingData: [player(10, 5, 100, 100, 140, 180), player(20, 5, 300, 200, 340, 280)],
  });

  const drawing = buildAnchoredDrawing({
    id: 'a', view: 'video', tool: 'arrow', color: '#fff', geometry: VIDEO_GEOMETRY,
    points: [{ x: 120, y: 140 }, { x: 220, y: 140 }],
    playerId: 5, anchorFrame: 10, anchor: { x: 120, y: 140 },
  });

  it('a pontokat a horgonyhoz képest tárolja', () => {
    expect(drawing.points[0]).toEqual({ dx: 0, dy: 0 });
    expect(drawing.points[1].dx).toBeCloseTo(100 / 1280);
  });

  it('a játékost követi: másik képkockán a horgony elmozdul, a rajz vele', () => {
    const atStart = resolveAnchoredPoints(drawing, VIDEO_GEOMETRY, tracking, 10);
    const later = resolveAnchoredPoints(drawing, VIDEO_GEOMETRY, tracking, 20);
    expect(atStart?.points[0]).toEqual({ x: 120, y: 140 });
    expect(later?.points[0]).toEqual({ x: 320, y: 240 });
    expect(later?.gap).toBe(0);
  });

  it('ha a játékos nincs a képkockán, a közelit használja halványításra jelezve', () => {
    expect(resolveAnchoredPoints(drawing, VIDEO_GEOMETRY, tracking, 14)?.gap).toBe(4);
  });

  it('ha a játékos a tűréshatáron túl is hiányzik, nem rajzol', () => {
    expect(resolveAnchoredPoints(drawing, VIDEO_GEOMETRY, tracking, 500)).toBeNull();
  });
});
