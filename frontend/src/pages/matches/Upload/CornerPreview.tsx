import { useEffect, useMemo, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react';
import { Box, IconButton, Stack, Typography } from '@mui/material';
import ZoomInIcon from '@mui/icons-material/ZoomIn';
import ZoomOutIcon from '@mui/icons-material/ZoomOut';
import CenterFocusStrongIcon from '@mui/icons-material/CenterFocusStrong';
import { useTranslation } from 'react-i18next';
import type { Corner } from '../hooks/useMatchUploadFlow';
import {
  GATE_CORNER_LABEL_KEYS,
  GATE_OUTLINE_ORDER,
  isPlaced,
  nextUnplacedIndex,
  replaceGate,
  withCornerAt,
  type GateDraft,
} from './gateDraft';
import {
  MAX_MAGNIFICATION,
  MIN_MAGNIFICATION,
  ZOOM_FACTOR_STEP,
  fitView,
  panBy,
  placementBounds,
  scaleOf,
  zoomAt,
  zoomCentered,
  type View,
} from './viewport';

interface CornerPreviewProps {
  imageUrl: string;
  initialCorners: Corner[];
  onChange: (corners: Corner[]) => void;
  /** The goals drawn on the same frame (orange). Controlled by the parent, which also shows the list. */
  gates: GateDraft[];
  onGatesChange: (gates: GateDraft[]) => void;
  /** Id of the goal whose next missing corner the next click on the image places; null = not placing. */
  placingGateId: number | null;
  onPlacingChange: (gateId: number | null) => void;
}

/**
 * The Python field-detection worker returns corners in this fixed order:
 *   [top_left, top_right, bottom_right, bottom_left]
 * (see corner_predictor.py). Labels follow the same order.
 */
const CORNER_LABEL_KEYS = [
  'upload.corners.topLeft',
  'upload.corners.topRight',
  'upload.corners.bottomRight',
  'upload.corners.bottomLeft',
] as const;

const HANDLE_RADIUS = 10;
const GATE_HANDLE_RADIUS = 8;
const GATE_COLOR = '#ff9800';

// The view (viewport.ts): a magnification (1 = the whole image fits the box, < 1 shows the margin around it so a corner beyond the frame
// can be placed, > 1 zooms into it so a goal post can be placed exactly) and the image point in the middle of the box. A corner can be
// placed anywhere in the frame plus half a frame on every side, whatever the view shows.
// Ctrl + wheel (and the pinch gesture of a trackpad, which the browser reports as Ctrl + wheel) zooms around the cursor; a plain wheel
// still scrolls the dialog.
const WHEEL_ZOOM_SENSITIVITY = 0.002; // one wheel notch (deltaY ~ 100) multiplies the magnification by ~1.22

type Layout = {
  imageOffsetX: number;
  imageOffsetY: number;
  scale: number;
  xMin: number;
  xMax: number;
  yMin: number;
  yMax: number;
};

/** What is being dragged: a field corner, or one corner of one goal. */
type Drag = { kind: 'field'; index: number } | { kind: 'gate'; gateId: number; index: number };

/** Pointer position (client coordinates) → image pixels, clamped to the area a corner can be placed in. */
const pointerToImage = (layout: Layout, container: HTMLElement, clientX: number, clientY: number) => {
  const rect = container.getBoundingClientRect();
  // Position relative to the container (not the image); subtract the image's offset within the
  // container, then divide by the per-pixel scale. The result may be negative (left of the image)
  // or > naturalW (right of it), bounded by the placement area (the frame plus its margin).
  const nx = (clientX - rect.left - layout.imageOffsetX) / layout.scale;
  const ny = (clientY - rect.top - layout.imageOffsetY) / layout.scale;
  return {
    x: Math.max(layout.xMin, Math.min(layout.xMax, nx)),
    y: Math.max(layout.yMin, Math.min(layout.yMax, ny)),
  };
};

export const CornerPreview = ({
  imageUrl,
  initialCorners,
  onChange,
  gates,
  onGatesChange,
  placingGateId,
  onPlacingChange,
}: CornerPreviewProps) => {
  const { t } = useTranslation();
  const imgRef = useRef<HTMLImageElement | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);

  const [natural, setNatural] = useState<{ w: number; h: number } | null>(null);
  const [displayed, setDisplayed] = useState<{ w: number; h: number } | null>(null);
  const [corners, setCorners] = useState<Corner[]>(initialCorners);
  const [drag, setDrag] = useState<Drag | null>(null);
  const [view, setView] = useState<View>({ magnification: 1, centerX: 0, centerY: 0 });
  // Dragging the background moves the view; `panStart` is where it began (pointer position and the view then).
  const [panning, setPanning] = useState(false);
  const panStartRef = useRef<{ x: number; y: number; view: View } | null>(null);
  // Space held while the pointer is over the preview: the next drag moves the view even while a goal corner is being placed.
  const [spaceHeld, setSpaceHeld] = useState(false);
  const hoveringRef = useRef(false);

  // The drag/click handlers read the latest goals through a ref, so the window listeners
  // are not torn down and re-registered on every pointer move.
  const gatesRef = useRef(gates);
  gatesRef.current = gates;

  // Keep parent in sync whenever the user moves a corner.
  useEffect(() => {
    onChange(corners);
  }, [corners, onChange]);

  // Reset corners when the upstream payload changes (e.g. retried detection).
  useEffect(() => {
    setCorners(initialCorners);
  }, [initialCorners]);

  // A new image (the first one, or the next after a camera change) starts with the whole frame in view.
  useEffect(() => {
    if (natural) setView(fitView(natural));
  }, [natural]);

  // Derived layout: image pixel → CSS pixel is `offset + pixel * scale`, the view centre is in the middle of the box.
  const layout = useMemo<Layout | null>(() => {
    if (!natural || !displayed) return null;
    const scale = scaleOf(view, natural, displayed); // image-pixel → CSS-pixel
    return {
      imageOffsetX: displayed.w / 2 - view.centerX * scale,
      imageOffsetY: displayed.h / 2 - view.centerY * scale,
      scale,
      ...placementBounds(natural),
    };
  }, [natural, displayed, view]);

  const handleImgLoad = () => {
    const el = imgRef.current;
    if (!el) return;
    setNatural({ w: el.naturalWidth, h: el.naturalHeight });
    // Container width is already known by now (responsive parent); we just need its
    // current rendered size. clientHeight will reflect the aspect-ratio CSS we apply
    // once `natural` is set.
    const container = containerRef.current;
    if (container) {
      // Defer one frame so the aspect-ratio CSS (which depends on `natural`) has been
      // applied and clientHeight is correct.
      requestAnimationFrame(() => {
        if (containerRef.current) {
          setDisplayed({
            w: containerRef.current.clientWidth,
            h: containerRef.current.clientHeight,
          });
        }
      });
    }
  };

  // Track resize so the corners stay anchored if the dialog re-flows.
  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const observer = new ResizeObserver(() => {
      const c = containerRef.current;
      if (c) setDisplayed({ w: c.clientWidth, h: c.clientHeight });
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, [natural]);

  // Global pointer handlers while dragging — registered on window so the cursor
  // can leave the handle without losing the drag.
  useEffect(() => {
    if (drag === null || !layout || !natural) return;

    const move = (e: PointerEvent) => {
      const container = containerRef.current;
      if (!container) return;
      const { x, y } = pointerToImage(layout, container, e.clientX, e.clientY);
      if (drag.kind === 'field') {
        setCorners((prev) =>
          prev.map((c, i) => (i === drag.index ? { x: Math.round(x), y: Math.round(y) } : c)),
        );
      } else {
        const gate = gatesRef.current.find((g) => g.id === drag.gateId);
        if (gate) onGatesChange(replaceGate(gatesRef.current, withCornerAt(gate, drag.index, x, y)));
      }
    };

    const up = () => setDrag(null);

    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
    window.addEventListener('pointercancel', up);
    return () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
      window.removeEventListener('pointercancel', up);
    };
  }, [drag, layout, natural, onGatesChange]);

  // Dragging the background moves the view (window listeners, so the pointer can leave the box).
  useEffect(() => {
    if (!panning || !natural || !displayed) return;
    const move = (e: PointerEvent) => {
      const start = panStartRef.current;
      if (start) setView(panBy(start.view, natural, displayed, e.clientX - start.x, e.clientY - start.y));
    };
    const up = () => {
      panStartRef.current = null;
      setPanning(false);
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
    window.addEventListener('pointercancel', up);
    return () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
      window.removeEventListener('pointercancel', up);
    };
  }, [panning, natural, displayed]);

  // Ctrl + wheel (a trackpad pinch too) zooms around the cursor. A native listener: React's onWheel is passive and could not stop the
  // page from zooming/scrolling.
  useEffect(() => {
    const el = containerRef.current;
    if (!el || !natural || !displayed) return;
    const onWheel = (e: WheelEvent) => {
      if (!e.ctrlKey && !e.metaKey) return; // a plain wheel scrolls the dialog
      e.preventDefault();
      const rect = el.getBoundingClientRect();
      const factor = Math.exp(-e.deltaY * WHEEL_ZOOM_SENSITIVITY);
      setView((v) => zoomAt(v, natural, displayed, factor, e.clientX - rect.left, e.clientY - rect.top));
    };
    el.addEventListener('wheel', onWheel, { passive: false });
    return () => el.removeEventListener('wheel', onWheel);
  }, [natural, displayed]);

  // Space (while the pointer is over the preview) switches a left-drag from "place a corner" to "move the view".
  useEffect(() => {
    const isTyping = () => {
      const a = document.activeElement;
      return !!a && (a.tagName === 'INPUT' || a.tagName === 'TEXTAREA' || a.tagName === 'SELECT' || (a as HTMLElement).isContentEditable);
    };
    const down = (e: KeyboardEvent) => {
      if (e.code !== 'Space' || !hoveringRef.current || isTyping()) return;
      e.preventDefault(); // no page scroll
      setSpaceHeld(true);
    };
    const up = (e: KeyboardEvent) => {
      if (e.code === 'Space') setSpaceHeld(false);
    };
    const blur = () => setSpaceHeld(false);
    window.addEventListener('keydown', down);
    window.addEventListener('keyup', up);
    window.addEventListener('blur', blur);
    return () => {
      window.removeEventListener('keydown', down);
      window.removeEventListener('keyup', up);
      window.removeEventListener('blur', blur);
    };
  }, []);

  const placingGate = placingGateId === null ? null : (gates.find((g) => g.id === placingGateId) ?? null);
  const placingIndex = placingGate ? nextUnplacedIndex(placingGate) : null;
  const isPlacing = placingGate !== null && placingIndex !== null;

  // Placing mode ends when the goal it was placing is gone (deleted) or already complete.
  useEffect(() => {
    if (placingGateId !== null && !isPlacing) onPlacingChange(null);
  }, [placingGateId, isPlacing, onPlacingChange]);

  // Escape cancels placing.
  useEffect(() => {
    if (!isPlacing) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onPlacingChange(null);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [isPlacing, onPlacingChange]);

  // A click on the image while placing puts the goal's next missing corner there.
  const handlePlaceClick = (e: ReactPointerEvent) => {
    if (!isPlacing || !layout || !containerRef.current || !placingGate || placingIndex === null) return;
    e.preventDefault();
    const { x, y } = pointerToImage(layout, containerRef.current, e.clientX, e.clientY);
    onGatesChange(replaceGate(gates, withCornerAt(placingGate, placingIndex, x, y)));
  };

  // Polygon vertices in container CSS coords.
  const polygonPoints = useMemo(() => {
    if (!layout) return '';
    return corners
      .map((c) => `${layout.imageOffsetX + c.x * layout.scale},${layout.imageOffsetY + c.y * layout.scale}`)
      .join(' ');
  }, [corners, layout]);

  const toCss = (x: number, y: number) =>
    layout ? { x: layout.imageOffsetX + x * layout.scale, y: layout.imageOffsetY + y * layout.scale } : { x: 0, y: 0 };

  // A press on the empty background: a plain left press moves the view, unless a goal corner is being placed (then it places the corner;
  // Space + drag or the middle button move the view instead). A press on a handle is that handle's own (it is not the container).
  const handleContainerPointerDown = (e: ReactPointerEvent) => {
    if (e.target !== e.currentTarget || !natural || !displayed) return;
    const movesView = e.button === 1 || (e.button === 0 && (spaceHeld || !isPlacing));
    if (movesView) {
      e.preventDefault();
      panStartRef.current = { x: e.clientX, y: e.clientY, view };
      setPanning(true);
      return;
    }
    if (e.button === 0) handlePlaceClick(e);
  };

  const zoomIn = () => natural && displayed && setView((v) => zoomCentered(v, natural, displayed, ZOOM_FACTOR_STEP));
  const zoomOut = () => natural && displayed && setView((v) => zoomCentered(v, natural, displayed, 1 / ZOOM_FACTOR_STEP));
  const resetZoom = () => natural && setView(fitView(natural));
  const isFit =
    !natural ||
    (view.magnification === 1 && Math.abs(view.centerX - natural.w / 2) < 0.5 && Math.abs(view.centerY - natural.h / 2) < 0.5);

  return (
    <Box>
      <Box
        ref={containerRef}
        onPointerDown={handleContainerPointerDown}
        onPointerEnter={() => {
          hoveringRef.current = true;
        }}
        onPointerLeave={() => {
          hoveringRef.current = false;
        }}
        sx={{
          position: 'relative',
          width: '100%',
          aspectRatio: natural ? `${natural.w} / ${natural.h}` : '16 / 9',
          overflow: 'hidden',
          // Subtle background so the off-image margin (visible when zoomed out)
          // is visually distinct from the defisheyed frame itself.
          bgcolor: 'rgba(0, 0, 0, 0.08)',
          userSelect: 'none',
          touchAction: 'none',
          cursor: panning ? 'grabbing' : isPlacing && !spaceHeld ? 'crosshair' : 'grab',
        }}
      >
        <Box
          component="img"
          ref={imgRef}
          src={imageUrl}
          alt="Field preview"
          onLoad={handleImgLoad}
          draggable={false}
          sx={{
            position: 'absolute',
            // Placed by the view: its top-left corner at the CSS position of image pixel (0, 0), `scale` CSS pixels per image pixel.
            // At magnification 1 it fills the box. Before the layout is known it simply fills the box.
            width: layout && natural ? natural.w * layout.scale : '100%',
            height: layout && natural ? natural.h * layout.scale : '100%',
            left: layout ? layout.imageOffsetX : 0,
            top: layout ? layout.imageOffsetY : 0,
            maxWidth: 'none',
            userSelect: 'none',
            pointerEvents: 'none',
          }}
        />

        {layout && (
          <Box
            component="svg"
            width={displayed!.w}
            height={displayed!.h}
            sx={{ position: 'absolute', top: 0, left: 0, pointerEvents: 'none' }}
          >
            <polygon
              points={polygonPoints}
              fill="rgba(33, 150, 243, 0.15)"
              stroke="rgba(33, 150, 243, 0.9)"
              strokeWidth={2}
            />
            {gates.map((gate) => {
              const outline = GATE_OUTLINE_ORDER.map((i) => gate.corners[i]);
              if (outline.every(isPlaced)) {
                const points = outline
                  .map((c) => {
                    const p = toCss(c.x as number, c.y as number);
                    return `${p.x},${p.y}`;
                  })
                  .join(' ');
                return (
                  <polygon
                    key={gate.id}
                    points={points}
                    fill="rgba(255, 152, 0, 0.18)"
                    stroke={GATE_COLOR}
                    strokeWidth={2}
                  />
                );
              }
              // Partial goal: only the sides whose two ends are both placed.
              return (
                <g key={gate.id}>
                  {outline.map((from, k) => {
                    const to = outline[(k + 1) % outline.length];
                    if (!isPlaced(from) || !isPlaced(to)) return null;
                    const a = toCss(from.x as number, from.y as number);
                    const b = toCss(to.x as number, to.y as number);
                    return (
                      <line key={k} x1={a.x} y1={a.y} x2={b.x} y2={b.y} stroke={GATE_COLOR} strokeWidth={2} strokeDasharray="6 4" />
                    );
                  })}
                </g>
              );
            })}
          </Box>
        )}

        {layout && corners.map((c, i) => {
          const cssX = layout.imageOffsetX + c.x * layout.scale;
          const cssY = layout.imageOffsetY + c.y * layout.scale;
          const isDragging = drag?.kind === 'field' && drag.index === i;
          // Visual cue when the corner is outside the image bounds. Doesn't affect
          // the stored pixel value; purely a hint that the corner is off-image.
          const isOutside =
            !!natural && (c.x < 0 || c.x > natural.w || c.y < 0 || c.y > natural.h);
          return (
            <Box
              key={i}
              onPointerDown={(e) => {
                if (isPlacing) return;
                e.preventDefault();
                (e.target as HTMLElement).setPointerCapture?.(e.pointerId);
                setDrag({ kind: 'field', index: i });
              }}
              sx={{
                position: 'absolute',
                left: cssX - HANDLE_RADIUS,
                top: cssY - HANDLE_RADIUS,
                width: HANDLE_RADIUS * 2,
                height: HANDLE_RADIUS * 2,
                borderRadius: '50%',
                bgcolor: isOutside ? 'warning.main' : 'primary.main',
                border: '2px solid white',
                boxShadow: 2,
                cursor: isDragging ? 'grabbing' : 'grab',
                touchAction: 'none',
                transition: isDragging ? 'none' : 'transform 60ms',
                transform: isDragging ? 'scale(1.2)' : 'none',
                // While placing a goal corner, clicks must reach the image underneath.
                pointerEvents: isPlacing ? 'none' : 'auto',
                zIndex: 2,
              }}
            >
              <Typography
                variant="caption"
                sx={{
                  position: 'absolute',
                  top: HANDLE_RADIUS * 2 + 4,
                  left: '50%',
                  transform: 'translateX(-50%)',
                  whiteSpace: 'nowrap',
                  bgcolor: 'rgba(0,0,0,0.7)',
                  color: 'white',
                  px: 0.75,
                  py: 0.25,
                  borderRadius: 0.5,
                  fontSize: 11,
                  pointerEvents: 'none',
                }}
              >
                {t(CORNER_LABEL_KEYS[i])}
              </Typography>
            </Box>
          );
        })}

        {layout && gates.map((gate, gateIndex) =>
          gate.corners.map((c, i) => {
            if (!isPlaced(c)) return null;
            const { x: cssX, y: cssY } = toCss(c.x as number, c.y as number);
            const isDragging = drag?.kind === 'gate' && drag.gateId === gate.id && drag.index === i;
            const isEstimated = c.source === 'estimated';
            // The label on the first corner names the goal; an untouched estimate is labelled as such.
            const caption = isEstimated
              ? t('upload.goals.estimatedCorner')
              : i === 0
                ? t('upload.goals.gate', { n: gateIndex + 1 })
                : null;
            return (
              <Box
                key={`${gate.id}-${i}`}
                title={t(GATE_CORNER_LABEL_KEYS[i])}
                onPointerDown={(e) => {
                  if (isPlacing) return;
                  e.preventDefault();
                  (e.target as HTMLElement).setPointerCapture?.(e.pointerId);
                  setDrag({ kind: 'gate', gateId: gate.id, index: i });
                }}
                sx={{
                  position: 'absolute',
                  left: cssX - GATE_HANDLE_RADIUS,
                  top: cssY - GATE_HANDLE_RADIUS,
                  width: GATE_HANDLE_RADIUS * 2,
                  height: GATE_HANDLE_RADIUS * 2,
                  borderRadius: '50%',
                  // An estimate is hollow with a dashed rim, so it cannot be mistaken for a detection.
                  bgcolor: isEstimated ? 'rgba(255,255,255,0.35)' : GATE_COLOR,
                  border: isEstimated ? `2px dashed ${GATE_COLOR}` : '2px solid white',
                  boxShadow: 2,
                  cursor: isDragging ? 'grabbing' : 'grab',
                  touchAction: 'none',
                  transition: isDragging ? 'none' : 'transform 60ms',
                  transform: isDragging ? 'scale(1.2)' : 'none',
                  pointerEvents: isPlacing ? 'none' : 'auto',
                  zIndex: 3,
                }}
              >
                {caption && (
                  <Typography
                    variant="caption"
                    sx={{
                      position: 'absolute',
                      bottom: GATE_HANDLE_RADIUS * 2 + 4,
                      left: '50%',
                      transform: 'translateX(-50%)',
                      whiteSpace: 'nowrap',
                      bgcolor: 'rgba(0,0,0,0.7)',
                      color: 'white',
                      px: 0.75,
                      py: 0.25,
                      borderRadius: 0.5,
                      fontSize: 11,
                      pointerEvents: 'none',
                    }}
                  >
                    {caption}
                  </Typography>
                )}
              </Box>
            );
          }),
        )}

        {isPlacing && placingIndex !== null && (
          <Typography
            variant="caption"
            sx={{
              position: 'absolute',
              top: 8,
              left: 8,
              bgcolor: 'rgba(0,0,0,0.75)',
              color: 'white',
              px: 1,
              py: 0.5,
              borderRadius: 0.5,
              pointerEvents: 'none',
              zIndex: 4,
            }}
          >
            {t('upload.goals.placeHint', { corner: t(GATE_CORNER_LABEL_KEYS[placingIndex]) })}
          </Typography>
        )}
      </Box>

      <Stack direction="row" spacing={0.5} sx={{ mt: 1, alignItems: 'center', justifyContent: 'center' }}>
        <IconButton size="small" onClick={zoomIn} disabled={view.magnification >= MAX_MAGNIFICATION} aria-label={t('upload.zoom.in')}>
          <ZoomInIcon fontSize="small" />
        </IconButton>
        <Typography variant="caption" sx={{ minWidth: 48, textAlign: 'center', fontVariantNumeric: 'tabular-nums' }}>
          {view.magnification.toFixed(1)}x
        </Typography>
        <IconButton size="small" onClick={zoomOut} disabled={view.magnification <= MIN_MAGNIFICATION} aria-label={t('upload.zoom.out')}>
          <ZoomOutIcon fontSize="small" />
        </IconButton>
        <IconButton size="small" onClick={resetZoom} disabled={isFit} aria-label={t('upload.zoom.reset')}>
          <CenterFocusStrongIcon fontSize="small" />
        </IconButton>
      </Stack>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', textAlign: 'center', mt: 0.5 }}>
        {t('upload.zoom.hint')}
      </Typography>
    </Box>
  );
};
