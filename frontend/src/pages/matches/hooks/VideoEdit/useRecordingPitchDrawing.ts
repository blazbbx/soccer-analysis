import { useDrawingCapture } from './useDrawingCapture';

export const useRecordingPitchDrawing = (
  canvasRef: React.RefObject<HTMLCanvasElement | null>
): void => useDrawingCapture(canvasRef, 'pitch');
