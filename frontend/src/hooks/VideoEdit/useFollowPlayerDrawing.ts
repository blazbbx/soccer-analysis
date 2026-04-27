import { useCallback, useEffect } from 'react';
import { useVideoPlayer } from '../../context/VideoPlayerContext';
import { useRecording } from '../../context/RecordingContext';
import type { TrackingFrameMap } from './useTrackingData';
import type { AnchoredDrawing } from '../../types/drawings';
import type { Point } from '../../utils/canvasDrawing';
import { useCanvasDrawing } from './useCanvasDrawing';

const PLAYER_BOX_COLORS = [
  '#f44336', '#ff9800', '#ffeb3b', '#4caf50',
  '#2196f3', '#9c27b0', '#00bcd4', '#ff5722',
];

function getScaleFactor(video: HTMLVideoElement, canvas: HTMLCanvasElement) {
  return {
    scaleX: video.videoWidth > 0 ? canvas.width / video.videoWidth : 1,
    scaleY: video.videoHeight > 0 ? canvas.height / video.videoHeight : 1,
  };
}

function getNearbyEntries(frameMap: TrackingFrameMap, frame: number) {
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
  videoFps: number,
  addAnchoredDrawing: (drawing: AnchoredDrawing) => void
): void => {
  const { isPlaying, currentTime } = useVideoPlayer();
  const {
    isRecording,
    followPlayerMode,
    selectedPlayerId,
    setSelectedPlayerId,
    activeDrawTool,
    activeDrawColor,
  } = useRecording();

  // Draw all player bounding boxes for the current frame (selection overlay)
  useEffect(() => {
    if (!followPlayerMode || selectedPlayerId !== null || !isRecording || isPlaying) return;

    const canvas = anchorCanvasRef.current;
    const video = videoRef.current;
    if (!canvas || !video) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const currentFrame = Math.round(currentTime * videoFps);
    const entries = getNearbyEntries(frameMap, currentFrame);
    const { scaleX, scaleY } = getScaleFactor(video, canvas);

    entries.forEach((entry, idx) => {
      const color = PLAYER_BOX_COLORS[idx % PLAYER_BOX_COLORS.length];
      const x = entry.x1 * scaleX;
      const y = entry.y1 * scaleY;
      const w = (entry.x2 - entry.x1) * scaleX;
      const h = (entry.y2 - entry.y1) * scaleY;

      ctx.strokeStyle = color;
      ctx.lineWidth = 2;
      ctx.strokeRect(x, y, w, h);

      ctx.fillStyle = color;
      ctx.font = 'bold 12px sans-serif';
      ctx.fillText(`#${entry.player_id}`, x + 3, y + 14);
    });
  }, [isPlaying, followPlayerMode, selectedPlayerId, isRecording, currentTime, frameMap, videoFps, anchorCanvasRef, videoRef]);

  // Handle click for player selection
  useEffect(() => {
    if (!followPlayerMode || selectedPlayerId !== null || !isRecording || isPlaying) return;

    const canvas = anchorCanvasRef.current;
    const video = videoRef.current;
    if (!canvas || !video) return;

    const handleClick = (e: MouseEvent) => {
      const rect = canvas.getBoundingClientRect();
      const clickX = (e.clientX - rect.left) * (canvas.width / rect.width);
      const clickY = (e.clientY - rect.top) * (canvas.height / rect.height);

      const currentFrame = Math.round(currentTime * videoFps);
      const entries = getNearbyEntries(frameMap, currentFrame);
      const { scaleX, scaleY } = getScaleFactor(video, canvas);

      for (const entry of entries) {
        const x = entry.x1 * scaleX;
        const y = entry.y1 * scaleY;
        const w = (entry.x2 - entry.x1) * scaleX;
        const h = (entry.y2 - entry.y1) * scaleY;

        if (clickX >= x && clickX <= x + w && clickY >= y && clickY <= y + h) {
          setSelectedPlayerId(entry.player_id);
          return;
        }
      }
    };

    canvas.addEventListener('mousedown', handleClick);
    return () => canvas.removeEventListener('mousedown', handleClick);
  }, [isPlaying, followPlayerMode, selectedPlayerId, isRecording, currentTime, frameMap, videoFps, setSelectedPlayerId, anchorCanvasRef, videoRef]);

  const onComplete = useCallback((rawPoints: Point[], canvas: HTMLCanvasElement) => {
    const video = videoRef.current;
    if (!video || selectedPlayerId === null) return;

    const currentFrame = Math.round(currentTime * videoFps);
    const entries = getNearbyEntries(frameMap, currentFrame);
    const entry = entries.find((en) => en.player_id === selectedPlayerId);
    if (!entry) return;

    const { scaleX, scaleY } = getScaleFactor(video, canvas);
    const cx = ((entry.x1 + entry.x2) / 2) * scaleX;
    const cy = ((entry.y1 + entry.y2) / 2) * scaleY;

    addAnchoredDrawing({
      id: `anchored-${Date.now()}`,
      type: 'anchored',
      view: 'video',
      playerId: selectedPlayerId,
      anchorFrame: currentFrame,
      tool: activeDrawTool === 'none' ? 'pen' : activeDrawTool,
      color: activeDrawColor,
      points: rawPoints.map((p) => ({
        dx: (p.x - cx) / canvas.width,
        dy: (p.y - cy) / canvas.height,
      })),
    });
  }, [videoRef, selectedPlayerId, currentTime, videoFps, frameMap, activeDrawTool, activeDrawColor, addAnchoredDrawing]);

  useCanvasDrawing({
    canvasRef: anchorCanvasRef,
    activeDrawTool,
    activeDrawColor,
    enabled: isRecording && followPlayerMode && selectedPlayerId !== null && activeDrawTool !== 'none',
    onComplete,
  });
};
