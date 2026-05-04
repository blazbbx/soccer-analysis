import { useCallback } from 'react';
import { useVideoPlayback } from '../../../../context/VideoPlayerContext';
import { useRecording } from '../../../../context/RecordingContext';
import { useCanvasDrawing } from './useCanvasDrawing';
import { getNearbyEntries } from './useFollowPlayerDrawing';
import type { TrackingFrameMap } from './useTrackingData';
import type { TrackingEntry } from '../../../../types/trackingData';
import type { Point } from '../../../../utils/canvasDrawing';

export type AnchorResolver = (
  entry: TrackingEntry,
  canvas: HTMLCanvasElement
) => { x: number; y: number } | null;

export const useAnchoredDrawingCapture = (
  canvasRef: React.RefObject<HTMLCanvasElement | null>,
  frameMap: TrackingFrameMap,
  videoFps: number,
  view: 'video' | 'pitch',
  resolveAnchor: AnchorResolver,
  clearOnComplete = false
): void => {
  const { currentTime } = useVideoPlayback();
  const {
    isRecording, followPlayerMode, selectedPlayerId,
    activeDrawTool, activeDrawColor, addDrawing,
  } = useRecording();

  const onComplete = useCallback((rawPoints: Point[], canvas: HTMLCanvasElement) => {
    if (selectedPlayerId === null) return;

    const currentFrame = Math.round(currentTime * videoFps);
    const entry = getNearbyEntries(frameMap, currentFrame).find((e) => e.player_id === selectedPlayerId);
    if (!entry) return;

    const anchor = resolveAnchor(entry, canvas);
    if (!anchor) return;

    addDrawing({
      id: `${view}-anchored-${Date.now()}`,
      type: 'anchored',
      view,
      playerId: selectedPlayerId,
      anchorFrame: currentFrame,
      tool: activeDrawTool === 'none' ? 'pen' : activeDrawTool,
      color: activeDrawColor,
      points: rawPoints.map((p) => ({
        dx: (p.x - anchor.x) / canvas.width,
        dy: (p.y - anchor.y) / canvas.height,
      })),
    });

    if (clearOnComplete) {
      const ctx = canvas.getContext('2d');
      if (ctx) ctx.clearRect(0, 0, canvas.width, canvas.height);
    }
  }, [view, selectedPlayerId, currentTime, videoFps, frameMap,
      activeDrawTool, activeDrawColor, addDrawing, resolveAnchor, clearOnComplete]);

  useCanvasDrawing({
    canvasRef,
    activeDrawTool,
    activeDrawColor,
    enabled: isRecording && followPlayerMode && selectedPlayerId !== null && activeDrawTool !== 'none',
    onComplete,
  });
};
