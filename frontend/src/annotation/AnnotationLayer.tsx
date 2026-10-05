import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react';
import { useFrameSnapshotSource } from '../player/hooks';
import type { ClipDrawing, DrawingTool, DrawingView } from '../types/drawings';
import { TRACKING_GAP_TOLERANCE } from '../types/drawings';
import type { Point } from '../utils/canvasDrawing';
import { inputModeOf } from './annotationStore';
import { useAnnotationState, useAnnotationStore } from './hooks';
import { buildAnchoredDrawing, buildStaticDrawing, isMeaningfulStroke } from './drawingModel';
import { renderChrome, renderContent, type StrokePreview } from './renderers';
import { PITCH_GEOMETRY, VIDEO_GEOMETRY, pickPlayer } from './viewGeometry';

export interface AnnotationLayerHandle {
  // A rajzok rétege: ez kerül a felvételbe.
  readonly contentCanvas: HTMLCanvasElement | null;
}

interface AnnotationLayerProps {
  view: DrawingView;
}

const FILL: React.CSSProperties = { position: 'absolute', top: 0, left: 0, width: '100%', height: '100%' };

// Egy nézet rajzfelülete: content (rajzok) és chrome (jelölők, előnézet) canvas, egyetlen bemenet-vezérlővel.
// A rajzolás a store és a pillanatkép változására imperatívan történik, nem React-renderrel.
export const AnnotationLayer = forwardRef<AnnotationLayerHandle, AnnotationLayerProps>(({ view }, ref) => {
  const geometry = view === 'video' ? VIDEO_GEOMETRY : PITCH_GEOMETRY;
  const annotation = useAnnotationStore();
  const snapshots = useFrameSnapshotSource();
  const mode = inputModeOf(useAnnotationState());

  const contentRef = useRef<HTMLCanvasElement>(null);
  const chromeRef = useRef<HTMLCanvasElement>(null);
  const previewRef = useRef<StrokePreview | null>(null);
  const redrawRef = useRef<() => void>(() => {});

  useImperativeHandle(ref, () => ({ get contentCanvas() { return contentRef.current; } }), []);

  useEffect(() => {
    const contentCtx = contentRef.current?.getContext('2d');
    const chromeCtx = chromeRef.current?.getContext('2d');
    if (!contentCtx || !chromeCtx) return;

    let lastDrawings: readonly ClipDrawing[] | null = null;
    let lastTracking: unknown = null;
    let chromeWasActive = true;

    const redraw = () => {
      const state = annotation.getState();
      const snapshot = snapshots.getSnapshot();
      const tracking = snapshots.getTracking();

      const hasAnchored = state.drawings.some((d) => d.view === view && d.type === 'anchored');
      if (state.drawings !== lastDrawings || tracking !== lastTracking || hasAnchored) {
        renderContent(contentCtx, { drawings: state.drawings, view, geometry, tracking, frame: snapshot.frame });
        lastDrawings = state.drawings;
        lastTracking = tracking;
      }

      const preview = previewRef.current;
      const chromeActive = state.followMode || preview !== null;
      if (chromeActive || chromeWasActive) {
        renderChrome(chromeCtx, { state, geometry, snapshot, tracking, preview });
      }
      chromeWasActive = chromeActive;
    };

    redrawRef.current = redraw;
    redraw();
    const unsubscribeAnnotation = annotation.subscribe(redraw);
    const unsubscribeSnapshots = snapshots.subscribe(redraw);
    return () => {
      unsubscribeAnnotation();
      unsubscribeSnapshots();
      redrawRef.current = () => {};
    };
  }, [annotation, snapshots, geometry, view]);

  useEffect(() => {
    const canvas = chromeRef.current;
    if (!canvas) return;

    let stroke: Point[] | null = null;
    let strokeTool: DrawingTool = 'pen';

    const toLogical = (e: PointerEvent): Point => {
      const rect = canvas.getBoundingClientRect();
      return {
        x: (e.clientX - rect.left) * (canvas.width / rect.width),
        y: (e.clientY - rect.top) * (canvas.height / rect.height),
      };
    };

    const updatePreview = () => {
      const state = annotation.getState();
      previewRef.current = stroke ? { tool: strokeTool, color: state.color, points: stroke } : null;
      redrawRef.current();
    };

    const onPointerDown = (e: PointerEvent) => {
      if (e.pointerType === 'mouse' && e.button !== 0) return;
      const state = annotation.getState();
      const point = toLogical(e);
      const currentMode = inputModeOf(state);

      if (currentMode === 'select') {
        const picked = pickPlayer(snapshots.getSnapshot().players, geometry, point.x, point.y);
        if (picked) annotation.selectPlayer(picked.player_id);
        return;
      }

      if (currentMode === 'free-draw' || currentMode === 'follow-draw') {
        canvas.setPointerCapture(e.pointerId);
        strokeTool = state.activeTool as DrawingTool;
        stroke = [point];
        updatePreview();
      }
    };

    const onPointerMove = (e: PointerEvent) => {
      if (!stroke) return;
      const point = toLogical(e);
      stroke = strokeTool === 'pen' ? [...stroke, point] : [stroke[0], point];
      updatePreview();
    };

    const finishStroke = () => {
      const points = stroke;
      stroke = null;
      updatePreview();
      if (!points || !isMeaningfulStroke(strokeTool, points)) return;

      const state = annotation.getState();
      const base = {
        id: `${view}-${Date.now()}`,
        view,
        tool: strokeTool,
        color: state.color,
        points,
        geometry,
      };

      if (inputModeOf(state) === 'free-draw') {
        annotation.addDrawing(buildStaticDrawing(base));
        return;
      }

      const playerId = state.selectedPlayerId;
      if (playerId === null) return;
      const snapshot = snapshots.getSnapshot();
      const found = snapshots.getTracking().findPlayer(snapshot.frame, playerId, TRACKING_GAP_TOLERANCE);
      const anchor = found ? geometry.anchorOf(found.entry) : null;
      if (!anchor) return;
      annotation.addDrawing(buildAnchoredDrawing({ ...base, playerId, anchorFrame: snapshot.frame, anchor }));
    };

    const cancelStroke = () => {
      stroke = null;
      updatePreview();
    };

    canvas.addEventListener('pointerdown', onPointerDown);
    canvas.addEventListener('pointermove', onPointerMove);
    canvas.addEventListener('pointerup', finishStroke);
    canvas.addEventListener('pointercancel', cancelStroke);
    return () => {
      canvas.removeEventListener('pointerdown', onPointerDown);
      canvas.removeEventListener('pointermove', onPointerMove);
      canvas.removeEventListener('pointerup', finishStroke);
      canvas.removeEventListener('pointercancel', cancelStroke);
    };
  }, [annotation, snapshots, geometry, view]);

  const interactive = mode !== 'passive';

  return (
    <>
      <canvas ref={contentRef} width={geometry.width} height={geometry.height} style={{ ...FILL, pointerEvents: 'none' }} />
      <canvas
        ref={chromeRef}
        width={geometry.width}
        height={geometry.height}
        style={{
          ...FILL,
          pointerEvents: interactive ? 'auto' : 'none',
          cursor: interactive ? 'crosshair' : 'default',
          touchAction: interactive ? 'none' : 'auto',
        }}
      />
    </>
  );
});
