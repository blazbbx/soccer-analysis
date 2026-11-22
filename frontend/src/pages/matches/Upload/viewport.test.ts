import { describe, expect, it } from 'vitest';
import {
  CORNER_MARGIN_FRAC,
  MAX_MAGNIFICATION,
  MIN_MAGNIFICATION,
  clampToPlacementBounds,
  fitView,
  panBy,
  placementBounds,
  scaleOf,
  toCss,
  toImage,
  zoomAt,
  zoomCentered,
  type Size,
  type View,
} from './viewport';

const NATURAL: Size = { w: 2560, h: 1440 };
const BOX: Size = { w: 800, h: 450 }; // the box has the aspect ratio of the image

const near = (a: number, b: number, digits = 6) => expect(a).toBeCloseTo(b, digits);

describe('fitView', () => {
  it('shows the whole image exactly in the box (the view of before the magnifier existed)', () => {
    const view = fitView(NATURAL);
    expect(view.magnification).toBe(1);
    expect(toCss(view, NATURAL, BOX, 0, 0)).toEqual({ x: 0, y: 0 });
    const corner = toCss(view, NATURAL, BOX, NATURAL.w, NATURAL.h);
    near(corner.x, BOX.w);
    near(corner.y, BOX.h);
  });
});

describe('toCss and toImage', () => {
  it('are inverse to each other at any view', () => {
    const views: View[] = [
      fitView(NATURAL),
      { magnification: 4, centerX: 900, centerY: 300 },
      { magnification: 0.5, centerX: 1280, centerY: 720 },
      { magnification: 12, centerX: -400, centerY: 1700 },
    ];
    for (const view of views) {
      for (const [x, y] of [[0, 0], [1280, 720], [-300, 2000], [2560, 1440]]) {
        const css = toCss(view, NATURAL, BOX, x, y);
        const back = toImage(view, NATURAL, BOX, css.x, css.y);
        near(back.x, x, 4);
        near(back.y, y, 4);
      }
    }
  });

  it('puts the view centre in the middle of the box', () => {
    const view: View = { magnification: 5, centerX: 700, centerY: 400 };
    const css = toCss(view, NATURAL, BOX, 700, 400);
    near(css.x, BOX.w / 2);
    near(css.y, BOX.h / 2);
  });

  it('scales by the magnification', () => {
    near(scaleOf({ magnification: 4, centerX: 0, centerY: 0 }, NATURAL, BOX), (BOX.w / NATURAL.w) * 4);
  });
});

describe('zoomAt', () => {
  it('keeps the image point under the cursor where it is (zooming in)', () => {
    const view = fitView(NATURAL);
    const cursor = { x: 610, y: 120 };
    const before = toImage(view, NATURAL, BOX, cursor.x, cursor.y);
    const zoomed = zoomAt(view, NATURAL, BOX, 4, cursor.x, cursor.y);
    const after = toImage(zoomed, NATURAL, BOX, cursor.x, cursor.y);
    near(after.x, before.x, 4);
    near(after.y, before.y, 4);
    expect(zoomed.magnification).toBe(4);
  });

  it('keeps the point under the cursor where it is on an already moved view, and when zooming out', () => {
    const view: View = { magnification: 6, centerX: 1900, centerY: 500 };
    for (const factor of [1 / 1.5, 1.5, 2]) {
      const cursor = { x: 80, y: 400 };
      const before = toImage(view, NATURAL, BOX, cursor.x, cursor.y);
      const next = zoomAt(view, NATURAL, BOX, factor, cursor.x, cursor.y);
      const after = toImage(next, NATURAL, BOX, cursor.x, cursor.y);
      near(after.x, before.x, 4);
      near(after.y, before.y, 4);
    }
  });

  it('stops at the limits and does not move the view there', () => {
    const maxed: View = { magnification: MAX_MAGNIFICATION, centerX: 900, centerY: 300 };
    expect(zoomAt(maxed, NATURAL, BOX, 2, 100, 100)).toBe(maxed);
    const mined: View = { magnification: MIN_MAGNIFICATION, centerX: 1280, centerY: 720 };
    expect(zoomAt(mined, NATURAL, BOX, 0.5, 100, 100)).toBe(mined);
    // a step that would pass the limit lands exactly on it
    expect(zoomAt(fitView(NATURAL), NATURAL, BOX, 100, 400, 225).magnification).toBe(MAX_MAGNIFICATION);
    expect(zoomAt(fitView(NATURAL), NATURAL, BOX, 0.001, 400, 225).magnification).toBe(MIN_MAGNIFICATION);
  });

  it('zooming out as far as possible shows the frame plus the margin on every side', () => {
    expect(MIN_MAGNIFICATION).toBe(0.5); // 1 / (1 + 2 * 0.5): the old maximum zoom of 2.0
    const out = zoomCentered(fitView(NATURAL), NATURAL, BOX, 0.001);
    const topLeft = toImage(out, NATURAL, BOX, 0, 0);
    const bottomRight = toImage(out, NATURAL, BOX, BOX.w, BOX.h);
    near(topLeft.x, -CORNER_MARGIN_FRAC * NATURAL.w, 3);
    near(topLeft.y, -CORNER_MARGIN_FRAC * NATURAL.h, 3);
    near(bottomRight.x, (1 + CORNER_MARGIN_FRAC) * NATURAL.w, 3);
    near(bottomRight.y, (1 + CORNER_MARGIN_FRAC) * NATURAL.h, 3);
  });

  it('zooming around the middle keeps the view centre', () => {
    const view: View = { magnification: 2, centerX: 1000, centerY: 600 };
    const next = zoomCentered(view, NATURAL, BOX, 3);
    near(next.centerX, 1000, 4);
    near(next.centerY, 600, 4);
    expect(next.magnification).toBe(6);
  });
});

describe('panBy', () => {
  it('moves the content with the pointer: the point under the pointer stays under it', () => {
    const view: View = { magnification: 5, centerX: 1280, centerY: 720 };
    const start = { x: 300, y: 200 };
    const grabbed = toImage(view, NATURAL, BOX, start.x, start.y);
    const moved = panBy(view, NATURAL, BOX, 70, -45); // the pointer moved by (70, -45)
    const underPointer = toImage(moved, NATURAL, BOX, start.x + 70, start.y - 45);
    near(underPointer.x, grabbed.x, 4);
    near(underPointer.y, grabbed.y, 4);
  });

  it('cannot move the centre out of the placement area', () => {
    const view: View = { magnification: 8, centerX: 1280, centerY: 720 };
    const far = panBy(view, NATURAL, BOX, 1e9, 1e9); // dragging the content far right/down moves the centre far left/up
    const b = placementBounds(NATURAL);
    expect(far.centerX).toBe(b.xMin);
    expect(far.centerY).toBe(b.yMin);
    const other = panBy(view, NATURAL, BOX, -1e9, -1e9);
    expect(other.centerX).toBe(b.xMax);
    expect(other.centerY).toBe(b.yMax);
  });
});

describe('placement bounds', () => {
  it('are the frame plus half a frame on every side, whatever the view', () => {
    expect(placementBounds(NATURAL)).toEqual({ xMin: -1280, xMax: 3840, yMin: -720, yMax: 2160 });
  });

  it('clamp a point to them', () => {
    expect(clampToPlacementBounds(NATURAL, -5000, 9000)).toEqual({ x: -1280, y: 2160 });
    expect(clampToPlacementBounds(NATURAL, 100, 200)).toEqual({ x: 100, y: 200 });
  });
});
