import { useCallback } from 'react';
import { useRecording } from '../../context/RecordingContext';
import { useCanvasDrawing } from './useCanvasDrawing';
import type { Point } from '../../utils/canvasDrawing';

export const useDrawingCapture = (
  canvasRef: React.RefObject<HTMLCanvasElement | null>,
  view: 'video' | 'pitch'
): void => {
  const { isRecording, activeDrawTool, activeDrawColor, addDrawing } = useRecording();

  const onComplete = useCallback((rawPoints: Point[], canvas: HTMLCanvasElement) => {
    if (!isRecording) return;
    addDrawing({
      id: `${view}-static-${Date.now()}`,
      type: 'static',
      view,
      tool: activeDrawTool === 'none' ? 'pen' : activeDrawTool,
      color: activeDrawColor,
      points: rawPoints.map((p) => ({ x: p.x / canvas.width, y: p.y / canvas.height })),
    });
  }, [isRecording, view, activeDrawTool, activeDrawColor, addDrawing]);

  useCanvasDrawing({
    canvasRef,
    activeDrawTool,
    activeDrawColor,
    enabled: isRecording && activeDrawTool !== 'none',
    onComplete,
  });
};
