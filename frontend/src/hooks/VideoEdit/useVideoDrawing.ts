import { useCallback, useEffect } from 'react';
import { useRecording } from '../../context/RecordingContext';
import { useCanvasDrawing } from './useCanvasDrawing';
import type { Point } from '../../utils/canvasDrawing';

export const useVideoDrawing = (
  canvasRef: React.RefObject<HTMLCanvasElement | null>,
  videoRef: React.RefObject<HTMLVideoElement | null>
) => {
  const {
    isRecording,
    activeDrawTool,
    activeDrawColor,
    undoTrigger,
    clearTrigger,
    addDrawing,
    undoLastDrawing,
    clearDrawings,
  } = useRecording();

  useEffect(() => {
    if (clearTrigger === 0 || !isRecording) return;
    clearDrawings();
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (ctx && canvas) ctx.clearRect(0, 0, canvas.width, canvas.height);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clearTrigger]);

  useEffect(() => {
    if (undoTrigger === 0 || !isRecording) return;
    undoLastDrawing();
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (ctx && canvas) ctx.clearRect(0, 0, canvas.width, canvas.height);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [undoTrigger]);

  useEffect(() => {
    const canvas = canvasRef.current;
    const video = videoRef.current;
    if (!canvas || !video) return;

    const resizeCanvas = () => {
      if (video.clientWidth > 0) {
        canvas.width = video.clientWidth;
        canvas.height = video.clientHeight;
      }
    };

    const observer = new ResizeObserver(resizeCanvas);
    observer.observe(video);
    return () => observer.disconnect();
  }, [canvasRef, videoRef]);

  const onComplete = useCallback((rawPoints: Point[], canvas: HTMLCanvasElement) => {
    if (!isRecording) return;

    const w = canvas.width;
    const h = canvas.height;

    addDrawing({
      id: `static-${Date.now()}`,
      type: 'static',
      view: 'video',
      tool: activeDrawTool === 'none' ? 'pen' : activeDrawTool,
      color: activeDrawColor,
      points: rawPoints.map((p) => ({ x: p.x / w, y: p.y / h })),
    });

    const ctx = canvas.getContext('2d');
    if (ctx) ctx.clearRect(0, 0, w, h);
  }, [isRecording, activeDrawTool, activeDrawColor, addDrawing]);

  useCanvasDrawing({
    canvasRef,
    activeDrawTool,
    activeDrawColor,
    enabled: isRecording && activeDrawTool !== 'none',
    onComplete,
  });
};
