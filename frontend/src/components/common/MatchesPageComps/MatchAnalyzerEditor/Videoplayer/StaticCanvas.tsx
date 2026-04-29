import React, { forwardRef, useImperativeHandle, useRef, useEffect } from 'react';
import { useRecording } from '../../../../../context/RecordingContext';
import { useStaticDrawing } from '../../../../../hooks/VideoEdit/useStaticDrawing';
import type { StaticDrawing } from '../../../../../types/drawings';
import { renderStaticDrawings } from '../../../../../utils/renderers/staticRenderer';

export interface StaticCanvasHandle {
  clear: () => void;
  canvasElement: HTMLCanvasElement | null;
}

interface StaticCanvasProps {
  videoRef: React.RefObject<HTMLVideoElement | null>;
}

export const StaticCanvas = forwardRef<StaticCanvasHandle, StaticCanvasProps>(
  ({ videoRef }, ref) => {
    const canvasRef = useRef<HTMLCanvasElement>(null);

    const {
      isRecording,
      followPlayerMode,
      drawingsRef,
      undoTrigger,
      clearTrigger,
      undoLastDrawing,
      clearDrawings,
    } = useRecording();

    useStaticDrawing(canvasRef);

    useImperativeHandle(ref, () => ({
      clear: () => {
        const canvas = canvasRef.current;
        const ctx = canvas?.getContext('2d');
        if (ctx && canvas) ctx.clearRect(0, 0, canvas.width, canvas.height);
      },
      get canvasElement() { return canvasRef.current; },
    }));

    // Size canvas to video display dimensions; restore static strokes after resize
    useEffect(() => {
      const video = videoRef.current;
      const canvas = canvasRef.current;
      if (!video || !canvas) return;

      const sync = () => {
        if (video.clientWidth === 0) return;
        canvas.width = video.clientWidth;
        canvas.height = video.clientHeight;
        const ctx = canvas.getContext('2d');
        const staticDrawings = drawingsRef.current.filter(
          (d): d is StaticDrawing => d.type === 'static' && d.view === 'video'
        );
        if (ctx && staticDrawings.length > 0) {
          renderStaticDrawings(ctx, staticDrawings, canvas.width, canvas.height);
        }
      };

      const observer = new ResizeObserver(sync);
      observer.observe(video);
      return () => observer.disconnect();
    }, [videoRef, drawingsRef]);

    // Clear all drawings
    useEffect(() => {
      if (clearTrigger === 0 || !isRecording) return;
      clearDrawings();
      const canvas = canvasRef.current;
      const ctx = canvas?.getContext('2d');
      if (ctx && canvas) ctx.clearRect(0, 0, canvas.width, canvas.height);
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [clearTrigger]);

    // Undo last drawing — re-render remaining static strokes
    useEffect(() => {
      if (undoTrigger === 0 || !isRecording) return;
      undoLastDrawing();
      const canvas = canvasRef.current;
      const ctx = canvas?.getContext('2d');
      if (!ctx || !canvas) return;
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      const staticDrawings = drawingsRef.current.filter(
        (d): d is StaticDrawing => d.type === 'static' && d.view === 'video'
      );
      if (staticDrawings.length > 0) {
        renderStaticDrawings(ctx, staticDrawings, canvas.width, canvas.height);
      }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [undoTrigger]);

    // Clear when recording stops
    useEffect(() => {
      if (isRecording) return;
      const canvas = canvasRef.current;
      const ctx = canvas?.getContext('2d');
      if (ctx && canvas) ctx.clearRect(0, 0, canvas.width, canvas.height);
    }, [isRecording]);

    return (
      <canvas
        ref={canvasRef}
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          width: '100%',
          height: '100%',
          pointerEvents: isRecording && !followPlayerMode ? 'auto' : 'none',
          cursor: isRecording && !followPlayerMode ? 'crosshair' : 'default',
        }}
      />
    );
  }
);
