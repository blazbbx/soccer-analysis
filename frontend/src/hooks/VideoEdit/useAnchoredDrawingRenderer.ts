import { useEffect } from 'react';
import { useVideoPlayer } from '../../context/VideoPlayerContext';
import { useRecording } from '../../context/RecordingContext';
import type { TrackingFrameMap } from './useTrackingData';
import type { AnchoredDrawing } from '../../types/drawings';
import { TRACKING_GAP_TOLERANCE } from '../../types/drawings';
import { renderAnchoredDrawings, findPlayerEntry } from '../../utils/renderers/anchoredRenderer';

function getScaleFactor(video: HTMLVideoElement, canvas: HTMLCanvasElement) {
  return {
    scaleX: video.videoWidth > 0 ? canvas.width / video.videoWidth : 1,
    scaleY: video.videoHeight > 0 ? canvas.height / video.videoHeight : 1,
  };
}

export const useAnchoredDrawingRenderer = (
  anchorCanvasRef: React.RefObject<HTMLCanvasElement | null>,
  videoRef: React.RefObject<HTMLVideoElement | null>,
  frameMap: TrackingFrameMap,
  videoFps: number,
  anchoredDrawings: AnchoredDrawing[]
): void => {
  const { currentTime, isPlaying } = useVideoPlayer();
  const { followPlayerMode, selectedPlayerId, isRecording } = useRecording();

  useEffect(() => {
    const canvas = anchorCanvasRef.current;
    const video = videoRef.current;
    if (!canvas || !video) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    const currentFrame = Math.round(currentTime * videoFps);
    const { scaleX, scaleY } = getScaleFactor(video, canvas);

    renderAnchoredDrawings(
      ctx,
      anchoredDrawings,
      canvas.width,
      canvas.height,
      currentFrame,
      frameMap,
      scaleX,
      scaleY
    );

    if (selectedPlayerId !== null && isRecording) {
      const result = findPlayerEntry(frameMap, currentFrame, selectedPlayerId, TRACKING_GAP_TOLERANCE);
      if (result) {
        const { entry } = result;
        const x = entry.x1 * scaleX;
        const y = entry.y1 * scaleY;
        const w = (entry.x2 - entry.x1) * scaleX;
        const h = (entry.y2 - entry.y1) * scaleY;

        ctx.save();
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 2;
        ctx.setLineDash([4, 3]);
        ctx.strokeRect(x, y, w, h);
        ctx.restore();
      }
    }
  }, [currentTime, isPlaying, followPlayerMode, selectedPlayerId, isRecording, frameMap, videoFps, anchorCanvasRef, videoRef, anchoredDrawings]);
};
