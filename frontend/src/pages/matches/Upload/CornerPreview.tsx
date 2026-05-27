import { useEffect, useMemo, useRef, useState } from 'react';
import { Box, IconButton, Stack, Typography } from '@mui/material';
import ZoomInIcon from '@mui/icons-material/ZoomIn';
import ZoomOutIcon from '@mui/icons-material/ZoomOut';
import CenterFocusStrongIcon from '@mui/icons-material/CenterFocusStrong';
import { useTranslation } from 'react-i18next';
import type { Corner } from '../hooks/useMatchUploadFlow';

interface CornerPreviewProps {
  imageUrl: string;
  initialCorners: Corner[];
  onChange: (corners: Corner[]) => void;
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

// Zoom factor `z` defines the *viewport size in image-pixel space*:
//   z = 1.0  → viewport = image, corners clamped to [0, W] × [0, H]
//   z = 1.5  → viewport = 1.5× image (image centered with 25% margin on each side),
//              corners can be placed in [-0.25W, 1.25W] × [-0.25H, 1.25H]
// Coaches need the upper bound so they can place a corner outside the image when the
// real field corner sits beyond the defisheyed frame.
const MIN_ZOOM = 1.0;
const MAX_ZOOM = 1.5;
const ZOOM_STEP = 0.1;

export const CornerPreview = ({ imageUrl, initialCorners, onChange }: CornerPreviewProps) => {
  const { t } = useTranslation();
  const imgRef = useRef<HTMLImageElement | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);

  const [natural, setNatural] = useState<{ w: number; h: number } | null>(null);
  const [displayed, setDisplayed] = useState<{ w: number; h: number } | null>(null);
  const [corners, setCorners] = useState<Corner[]>(initialCorners);
  const [dragIndex, setDragIndex] = useState<number | null>(null);
  const [zoom, setZoom] = useState<number>(1.0);

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
  const layout = useMemo(() => {
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
    if (dragIndex === null || !layout || !natural) return;

    const move = (e: PointerEvent) => {
      const container = containerRef.current;
      if (!container) return;
      const rect = container.getBoundingClientRect();
      // Mouse position relative to the container (not the image).
      const localX = e.clientX - rect.left;
      const localY = e.clientY - rect.top;
      // Map back to image-pixel space: subtract the image's offset within the
      // container, then divide by the per-pixel scale. The result is allowed to be
      // negative (corner left of the image) or > naturalW (right of the image),
      // bounded by the current viewport.
      const nx = (localX - layout.imageOffsetX) / layout.scale;
      const ny = (localY - layout.imageOffsetY) / layout.scale;
      const clampedX = Math.max(layout.xMin, Math.min(layout.xMax, nx));
      const clampedY = Math.max(layout.yMin, Math.min(layout.yMax, ny));
      setCorners((prev) =>
        prev.map((c, i) => (i === dragIndex ? { x: Math.round(clampedX), y: Math.round(clampedY) } : c)),
      );
    };

    const up = () => setDragIndex(null);

    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
    window.addEventListener('pointercancel', up);
    return () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
      window.removeEventListener('pointercancel', up);
    };
  }, [dragIndex, layout, natural]);

  // Polygon vertices in container CSS coords.
  const polygonPoints = useMemo(() => {
    if (!layout) return '';
    return corners
      .map((c) => `${layout.imageOffsetX + c.x * layout.scale},${layout.imageOffsetY + c.y * layout.scale}`)
      .join(' ');
  }, [corners, layout]);

  const zoomIn = () => setZoom((z) => Math.max(MIN_ZOOM, +(z - ZOOM_STEP).toFixed(2)));
  const zoomOut = () => setZoom((z) => Math.min(MAX_ZOOM, +(z + ZOOM_STEP).toFixed(2)));
  const resetZoom = () => setZoom(1.0);

  return (
    <Box>
      <Box
        ref={containerRef}
        sx={{
          position: 'relative',
          width: '100%',
          aspectRatio: natural ? `${natural.w} / ${natural.h}` : '16 / 9',
          overflow: 'hidden',
          // Subtle background so the off-image margin (visible when zoomed out)
          // is visually distinct from the defisheyed frame itself.
          bgcolor: 'rgba(0, 0, 0, 0.08)',
          userSelect: 'none',
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
          </Box>
        )}

        {layout && corners.map((c, i) => {
          const cssX = layout.imageOffsetX + c.x * layout.scale;
          const cssY = layout.imageOffsetY + c.y * layout.scale;
          const isDragging = dragIndex === i;
          // Visual cue when the corner is outside the image bounds. Doesn't affect
          // the stored pixel value; purely a hint that the corner is off-image.
          const isOutside =
            !!natural && (c.x < 0 || c.x > natural.w || c.y < 0 || c.y > natural.h);
          return (
            <Box
              key={i}
              onPointerDown={(e) => {
                e.preventDefault();
                (e.target as HTMLElement).setPointerCapture?.(e.pointerId);
                setDragIndex(i);
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
