import { useCallback, useEffect } from 'react';
import { useClip } from '../../context/ClipContext';
import { useCanvasDrawing } from './useCanvasDrawing';
import type { Point } from '../../utils/canvasDrawing';

export const usePitchDrawing = (
  canvasRef: React.RefObject<HTMLCanvasElement | null>,
  containerRef: React.RefObject<HTMLDivElement | null>
) => {
  const {
    activeDrawTool, activeDrawColor, undoTrigger, clearTrigger,
    drawingClipId, addDrawingToClip, undoLastDrawingFromClip, clearDrawingsFromClip,
  } = useClip();

  useEffect(() => {
    const canvas = canvasRef.current;
    const container = containerRef.current;
    if (!canvas || !container) return;

    const sync = () => {
      if (container.clientWidth > 0) {
        canvas.width = container.clientWidth;
        canvas.height = container.clientHeight;
      }
    };
    sync();

    const ro = new ResizeObserver(sync);
    ro.observe(container);
    return () => ro.disconnect();
  }, [canvasRef, containerRef]);

  useEffect(() => {
    if (clearTrigger === 0 || !drawingClipId) return;
    clearDrawingsFromClip(drawingClipId);
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (ctx && canvas) ctx.clearRect(0, 0, canvas.width, canvas.height);
  }, [clearTrigger]);

  useEffect(() => {
    if (undoTrigger === 0 || !drawingClipId) return;
    undoLastDrawingFromClip(drawingClipId);
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (ctx && canvas) ctx.clearRect(0, 0, canvas.width, canvas.height);
  }, [undoTrigger]);

  const onComplete = useCallback((rawPoints: Point[], canvas: HTMLCanvasElement) => {
    if (!drawingClipId) return;

    const w = canvas.width;
    const h = canvas.height;

    addDrawingToClip(drawingClipId, {
      id: `pitch-${Date.now()}`,
      type: 'static',
      view: 'pitch',
      tool: activeDrawTool === 'none' ? 'pen' : activeDrawTool,
      color: activeDrawColor,
      points: rawPoints.map((p) => ({ x: p.x / w, y: p.y / h })),
    });

    const ctx = canvas.getContext('2d');
    if (ctx) ctx.clearRect(0, 0, w, h);
  }, [drawingClipId, activeDrawTool, activeDrawColor, addDrawingToClip]);

  useCanvasDrawing({
    canvasRef,
    activeDrawTool,
    activeDrawColor,
    enabled: drawingClipId !== null && activeDrawTool !== 'none',
    onComplete,
  });
};
