import { useDrawingCapture } from './useDrawingCapture';

export const useStaticDrawing = (
  canvasRef: React.RefObject<HTMLCanvasElement | null>
): void => useDrawingCapture(canvasRef, 'video');
