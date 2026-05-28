import { useEffect, useRef } from 'react';
import { drawArrow, drawCircle, type Point } from '../../../../utils/canvasDrawing';
import type { DrawingTool } from '../../../../types/drawings';

interface UseCanvasDrawingOptions {
  canvasRef: React.RefObject<HTMLCanvasElement | null>;
  activeDrawTool: DrawingTool | 'none';
  activeDrawColor: string;
  enabled: boolean;
  onComplete: (rawPoints: Point[], canvas: HTMLCanvasElement) => void;
}

export function useCanvasDrawing({
  canvasRef,
  activeDrawTool,
  activeDrawColor,
  enabled,
  onComplete,
}: UseCanvasDrawingOptions): void {
  const isDrawing = useRef(false);
  const startPos = useRef<Point>({ x: 0, y: 0 });
  const penPoints = useRef<Point[]>([]);
  const savedSnapshot = useRef<ImageData | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !enabled || activeDrawTool === 'none') return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const getMousePos = (evt: MouseEvent): Point => {
      const rect = canvas.getBoundingClientRect();
      return {
        x: (evt.clientX - rect.left) * (canvas.width / rect.width),
        y: (evt.clientY - rect.top) * (canvas.height / rect.height),
      };
    };

    const handleMouseDown = (e: MouseEvent) => {
      isDrawing.current = true;
      startPos.current = getMousePos(e);
      penPoints.current = [startPos.current];
      savedSnapshot.current = ctx.getImageData(0, 0, canvas.width, canvas.height);

      ctx.strokeStyle = activeDrawColor;
      ctx.fillStyle = activeDrawColor;
      ctx.lineWidth = 3;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';

      if (activeDrawTool === 'pen') {
        ctx.beginPath();
        ctx.moveTo(startPos.current.x, startPos.current.y);
      }
    };

    const handleMouseMove = (e: MouseEvent) => {
      if (!isDrawing.current) return;
      const currentPos = getMousePos(e);

      if (activeDrawTool === 'pen') {
        penPoints.current.push(currentPos);
        ctx.lineTo(currentPos.x, currentPos.y);
        ctx.stroke();
      } else {
        if (savedSnapshot.current) ctx.putImageData(savedSnapshot.current, 0, 0);
        if (activeDrawTool === 'arrow') {
          drawArrow(ctx, startPos.current, currentPos, activeDrawColor);
        } else if (activeDrawTool === 'circle') {
          drawCircle(ctx, startPos.current, currentPos, activeDrawColor);
        }
      }
    };

    const handleMouseUp = (e: MouseEvent) => {
      if (!isDrawing.current) return;
      isDrawing.current = false;

      const currentPos = getMousePos(e);
      const rawPoints = activeDrawTool === 'pen'
        ? penPoints.current
        : [startPos.current, currentPos];

      onComplete(rawPoints, canvas);
    };

    canvas.addEventListener('mousedown', handleMouseDown);
    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);

    return () => {
      canvas.removeEventListener('mousedown', handleMouseDown);
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [canvasRef, activeDrawTool, activeDrawColor, enabled, onComplete]);
}
