/**
 * The view of the corner/goal preview: how much the image is magnified and which point of it is in the middle of the box.
 * Pure geometry (no React, no DOM) so it can be tested. Image coordinates are pixels of the defished frame; CSS coordinates are
 * pixels inside the preview box.
 *
 * A view is `magnification` (1 = the whole image fits the box, exactly as before the magnifier existed; < 1 shows the margin around the
 * image; > 1 zooms into it) and the image point `(centerX, centerY)` in the middle of the box. The box always has the aspect ratio of the
 * image, so one scale serves both axes.
 */

export interface Size {
  w: number;
  h: number;
}

export interface View {
  magnification: number;
  centerX: number;
  centerY: number;
}

/**
 * How far beyond each edge of the frame a corner can be placed, as a fraction of the frame: half a frame on every side. The real field
 * corner can sit beyond the defished frame, and the user must be able to place it there. (Before the magnifier the limit was a quarter
 * of a frame.)
 */
export const CORNER_MARGIN_FRAC = 0.5;

/** The most one can zoom out: the frame plus its margin on both sides fits the box. */
export const MIN_MAGNIFICATION = 1 / (1 + 2 * CORNER_MARGIN_FRAC);

/** The most one can zoom in: at 12x a 2560 px wide frame shows ~213 px across a 1000 px box, enough to place a goal post exactly. */
export const MAX_MAGNIFICATION = 12;

/** One step of the zoom buttons. */
export const ZOOM_FACTOR_STEP = 1.5;

export const fitView = (natural: Size): View => ({ magnification: 1, centerX: natural.w / 2, centerY: natural.h / 2 });

/** CSS pixels per image pixel. */
export const scaleOf = (view: View, natural: Size, box: Size): number => (box.w / natural.w) * view.magnification;

/** Image pixels → CSS pixels of the box. */
export const toCss = (view: View, natural: Size, box: Size, x: number, y: number) => {
  const scale = scaleOf(view, natural, box);
  return { x: box.w / 2 + (x - view.centerX) * scale, y: box.h / 2 + (y - view.centerY) * scale };
};

/** CSS pixels of the box → image pixels (not clamped). */
export const toImage = (view: View, natural: Size, box: Size, cssX: number, cssY: number) => {
  const scale = scaleOf(view, natural, box);
  return { x: view.centerX + (cssX - box.w / 2) / scale, y: view.centerY + (cssY - box.h / 2) / scale };
};

/** The rectangle (image pixels) a corner can be placed in: the frame plus the margin on every side. */
export const placementBounds = (natural: Size) => ({
  xMin: -CORNER_MARGIN_FRAC * natural.w,
  xMax: (1 + CORNER_MARGIN_FRAC) * natural.w,
  yMin: -CORNER_MARGIN_FRAC * natural.h,
  yMax: (1 + CORNER_MARGIN_FRAC) * natural.h,
});

export const clampToPlacementBounds = (natural: Size, x: number, y: number) => {
  const b = placementBounds(natural);
  return { x: Math.max(b.xMin, Math.min(b.xMax, x)), y: Math.max(b.yMin, Math.min(b.yMax, y)) };
};

const clamp = (value: number, min: number, max: number) => Math.max(min, Math.min(max, value));

/** The view centre may not leave the placement area, so the view cannot be lost in the empty space. */
const clampCenter = (natural: Size, view: View): View => {
  const b = placementBounds(natural);
  return { ...view, centerX: clamp(view.centerX, b.xMin, b.xMax), centerY: clamp(view.centerY, b.yMin, b.yMax) };
};

/**
 * Multiplies the magnification by `factor` and keeps the image point under the CSS position `(cssX, cssY)` where it is (the way a map
 * zooms around the cursor). At the limits the magnification stops and the view does not move.
 */
export const zoomAt = (view: View, natural: Size, box: Size, factor: number, cssX: number, cssY: number): View => {
  const magnification = clamp(view.magnification * factor, MIN_MAGNIFICATION, MAX_MAGNIFICATION);
  if (magnification === view.magnification) return view;
  const anchor = toImage(view, natural, box, cssX, cssY);
  const scale = scaleOf({ ...view, magnification }, natural, box);
  return clampCenter(natural, {
    magnification,
    centerX: anchor.x - (cssX - box.w / 2) / scale,
    centerY: anchor.y - (cssY - box.h / 2) / scale,
  });
};

/** Zooms around the middle of the box (the buttons). */
export const zoomCentered = (view: View, natural: Size, box: Size, factor: number): View =>
  zoomAt(view, natural, box, factor, box.w / 2, box.h / 2);

/** Moves the content by `(dxCss, dyCss)` CSS pixels, as when the image is dragged. */
export const panBy = (view: View, natural: Size, box: Size, dxCss: number, dyCss: number): View => {
  const scale = scaleOf(view, natural, box);
  return clampCenter(natural, { ...view, centerX: view.centerX - dxCss / scale, centerY: view.centerY - dyCss / scale });
};
