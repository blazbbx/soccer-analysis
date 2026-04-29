import { useCallback } from 'react';
import type { TrackingFrameMap } from './useTrackingData';
import { useAnchoredDrawingCapture } from './useAnchoredDrawingCapture';

export const PLAYER_BOX_COLORS = [
  '#f44336', '#ff9800', '#ffeb3b', '#4caf50',
  '#2196f3', '#9c27b0', '#00bcd4', '#ff5722',
];

export function getScaleFactor(video: HTMLVideoElement, canvas: HTMLCanvasElement) {
  return {
    scaleX: video.videoWidth > 0 ? canvas.width / video.videoWidth : 1,
    scaleY: video.videoHeight > 0 ? canvas.height / video.videoHeight : 1,
  };
}

export function getNearbyEntries(frameMap: TrackingFrameMap, frame: number) {
  let entries = frameMap.get(frame) ?? [];
  if (entries.length === 0) {
    for (let offset = 1; offset <= 5; offset++) {
      entries = frameMap.get(frame + offset) ?? frameMap.get(frame - offset) ?? [];
      if (entries.length > 0) break;
    }
  }
  return entries;
}

export const useFollowPlayerDrawing = (
  anchorCanvasRef: React.RefObject<HTMLCanvasElement | null>,
  videoRef: React.RefObject<HTMLVideoElement | null>,
  frameMap: TrackingFrameMap,
  videoFps: number
): void => {
  const resolveAnchor = useCallback((entry: { x1: number; x2: number; y1: number; y2: number }, canvas: HTMLCanvasElement) => {
    const video = videoRef.current;
    if (!video) return null;
    const { scaleX, scaleY } = getScaleFactor(video, canvas);
    return {
      x: ((entry.x1 + entry.x2) / 2) * scaleX,
      y: ((entry.y1 + entry.y2) / 2) * scaleY,
    };
  }, [videoRef]);

  useAnchoredDrawingCapture(anchorCanvasRef, frameMap, videoFps, 'video', resolveAnchor);
};
