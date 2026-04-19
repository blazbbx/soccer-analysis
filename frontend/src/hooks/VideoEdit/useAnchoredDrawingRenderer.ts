import { useEffect } from 'react';
import { useVideoPlayer } from '../../context/VideoPlayerContext';
import type { TrackingFrameMap } from './useTrackingData';
import type { AnchoredDrawing } from '../../types/anchoredDrawing';
import { TRACKING_GAP_TOLERANCE } from '../../types/anchoredDrawing';

import { drawArrow, drawCircle, drawPenPath, type Point } from '../../utils/canvasDrawing';
import type { TrackingEntry } from '../../types/trackingData';

function getScaleFactor(video: HTMLVideoElement, canvas: HTMLCanvasElement) {
  return {
    scaleX: video.videoWidth > 0 ? canvas.width / video.videoWidth : 1,
    scaleY: video.videoHeight > 0 ? canvas.height / video.videoHeight : 1,
  };
}

function findPlayerEntry(
  frameMap: TrackingFrameMap,
  targetFrame: number,
  playerId: number,
  tolerance: number
): { entry: TrackingEntry; gap: number } | null {
  for (let gap = 0; gap <= tolerance; gap++) {
    const frame = targetFrame - gap;
    if (frame < 0) break;
    const entries = frameMap.get(frame);
    if (!entries) continue;
    const entry = entries.find((e) => e.player_id === playerId);
    if (entry) return { entry, gap };
  }
  return null;
}

export const useAnchoredDrawingRenderer = (
  anchorCanvasRef: React.RefObject<HTMLCanvasElement | null>,
  videoRef: React.RefObject<HTMLVideoElement | null>,
  frameMap: TrackingFrameMap,
  videoFps: number,
  anchoredDrawingsRef: React.MutableRefObject<AnchoredDrawing[]>
): void => {
  const { currentTime, followPlayerMode, selectedPlayerId } = useVideoPlayer();

  useEffect(() => {
    const canvas = anchorCanvasRef.current;
    const video = videoRef.current;
    if (!canvas || !video) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    const drawings = anchoredDrawingsRef.current;
    const currentFrame = Math.round(currentTime * videoFps);
    const { scaleX, scaleY } = getScaleFactor(video, canvas);

    for (const drawing of drawings) {
      const result = findPlayerEntry(frameMap, currentFrame, drawing.playerId, TRACKING_GAP_TOLERANCE);
      if (!result) continue;

      const { entry, gap } = result;
      ctx.globalAlpha = gap > 0 ? 0.4 : 1.0;

      const cx = ((entry.x1 + entry.x2) / 2) * scaleX;
      const cy = ((entry.y1 + entry.y2) / 2) * scaleY;

      const pixelPoints: Point[] = drawing.points.map((p) => ({
        x: cx + p.dx * canvas.width,
        y: cy + p.dy * canvas.height,
      }));

      if (drawing.tool === 'pen') {
        drawPenPath(ctx, pixelPoints, drawing.color);
      } else if (drawing.tool === 'arrow' && pixelPoints.length >= 2) {
        drawArrow(ctx, pixelPoints[0], pixelPoints[pixelPoints.length - 1], drawing.color);
      } else if (drawing.tool === 'circle' && pixelPoints.length >= 2) {
        drawCircle(ctx, pixelPoints[0], pixelPoints[1], drawing.color);
      }
    }

    ctx.globalAlpha = 1.0;

    // Show a dashed outline around the selected player during playback
    if (selectedPlayerId !== null) {
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
  }, [currentTime, followPlayerMode, selectedPlayerId, frameMap, videoFps, anchorCanvasRef, videoRef, anchoredDrawingsRef]);
};
