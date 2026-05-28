import { useCallback } from 'react';
import { pitchToCanvas } from '../../../../utils/renderers/pitchAnchoredRenderer';
import { useAnchoredDrawingCapture } from './useAnchoredDrawingCapture';
import type { TrackingFrameMap } from './useTrackingData';
import type { TrackingEntry } from '../../../../types/trackingData';

export const useRecordingPitchFollowDrawing = (
  canvasRef: React.RefObject<HTMLCanvasElement | null>,
  frameMap: TrackingFrameMap,
  videoFps: number
): void => {
  const resolveAnchor = useCallback((entry: TrackingEntry, canvas: HTMLCanvasElement) => {
    if (entry.tx == null || entry.ty == null) return null;
    return pitchToCanvas(entry.tx, entry.ty, canvas.width, canvas.height);
  }, []);

  useAnchoredDrawingCapture(canvasRef, frameMap, videoFps, 'pitch', resolveAnchor, true);
};
