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

// Zoom factor `z` defines the *viewport size in image-pixel space*:
//   z = 1.0  → viewport = image, corners clamped to [0, W] × [0, H]
//   z = 1.5  → viewport = 1.5× image (image centered with 25% margin on each side),
//              corners can be placed in [-0.25W, 1.25W] × [-0.25H, 1.25H]
// Coaches need the upper bound so they can place a corner outside the image when the
// real field corner sits beyond the defisheyed frame.
const MIN_ZOOM = 1.0;
const MAX_ZOOM = 1.5;
const ZOOM_STEP = 0.1;

type Layout = {
  imageCssW: number;
  imageCssH: number;
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

/** Pointer position (client coordinates) → image pixels, clamped to what the current zoom lets you reach. */
const pointerToImage = (layout: Layout, container: HTMLElement, clientX: number, clientY: number) => {
  const rect = container.getBoundingClientRect();
  // Position relative to the container (not the image); subtract the image's offset within the
  // container, then divide by the per-pixel scale. The result may be negative (left of the image)
  // or > naturalW (right of it), bounded by the current viewport.
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
  const [zoom, setZoom] = useState<number>(1.0);

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

  // Derived layout. `imageOffset` is in container CSS pixels and represents where the
  // (smaller) image sits inside the (larger) viewport when zoomed out.
  const layout = useMemo<Layout | null>(() => {
    if (!natural || !displayed) return null;
    const imageCssW = displayed.w / zoom;
    const imageCssH = displayed.h / zoom;
    const imageOffsetX = (displayed.w - imageCssW) / 2;
    const imageOffsetY = (displayed.h - imageCssH) / 2;
    const scale = imageCssW / natural.w; // image-pixel → CSS-pixel
    // Corner clamp bounds in image-pixel space.
    const xMin = -((zoom - 1) * natural.w) / 2;
    const xMax = ((zoom + 1) * natural.w) / 2;
    const yMin = -((zoom - 1) * natural.h) / 2;
    const yMax = ((zoom + 1) * natural.h) / 2;
    return { imageCssW, imageCssH, imageOffsetX, imageOffsetY, scale, xMin, xMax, yMin, yMax };
  }, [natural, displayed, zoom]);

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

  const zoomIn = () => setZoom((z) => Math.max(MIN_ZOOM, +(z - ZOOM_STEP).toFixed(2)));
  const zoomOut = () => setZoom((z) => Math.min(MAX_ZOOM, +(z + ZOOM_STEP).toFixed(2)));
  const resetZoom = () => setZoom(1.0);

  return (
    <Box>
      <Box
        ref={containerRef}
        onPointerDown={handlePlaceClick}
        sx={{
          position: 'relative',
          width: '100%',
          aspectRatio: natural ? `${natural.w} / ${natural.h}` : '16 / 9',
          overflow: 'hidden',
          // Subtle background so the off-image margin (visible when zoomed out)
          // is visually distinct from the defisheyed frame itself.
          bgcolor: 'rgba(0, 0, 0, 0.08)',
          userSelect: 'none',
          cursor: isPlacing ? 'crosshair' : 'default',
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
            // Image fills `1/zoom` of the container, centered. At z=1 it fills 100%
            // and the offsets collapse to 0. At z=1.5 it fills ~66.7% with ~16.7%
            // margin on each side.
            width: `${100 / zoom}%`,
            height: `${100 / zoom}%`,
            left: `${((1 - 1 / zoom) / 2) * 100}%`,
            top: `${((1 - 1 / zoom) / 2) * 100}%`,
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
        <IconButton size="small" onClick={zoomIn} disabled={zoom <= MIN_ZOOM} aria-label={t('upload.zoom.in')}>
          <ZoomInIcon fontSize="small" />
        </IconButton>
        <Typography variant="caption" sx={{ minWidth: 48, textAlign: 'center', fontVariantNumeric: 'tabular-nums' }}>
          {zoom.toFixed(1)}x
        </Typography>
        <IconButton size="small" onClick={zoomOut} disabled={zoom >= MAX_ZOOM} aria-label={t('upload.zoom.out')}>
          <ZoomOutIcon fontSize="small" />
        </IconButton>
        <IconButton size="small" onClick={resetZoom} disabled={zoom === 1.0} aria-label={t('upload.zoom.reset')}>
          <CenterFocusStrongIcon fontSize="small" />
        </IconButton>
      </Stack>
    </Box>
  );
};
