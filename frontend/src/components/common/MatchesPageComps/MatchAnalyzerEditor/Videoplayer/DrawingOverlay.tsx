import React, { useRef, useEffect } from 'react';
import { useVideoPlayer } from '../../../../../context/VideoPlayerContext';
import type { ClipDrawing } from '../../../../../types/anchoredDrawing';
import { TRACKING_GAP_TOLERANCE } from '../../../../../types/anchoredDrawing';
import { drawPenPath, drawArrow, drawCircle } from '../../../../../utils/canvasDrawing';
import type { TrackingFrameMap } from '../../../../../hooks/VideoEdit/useTrackingData';
import type { TrackingEntry } from '../../../../../types/trackingData';

interface DrawingOverlayProps {
  videoRef: React.RefObject<HTMLVideoElement | null>;
  frameMap: TrackingFrameMap;
  videoFps: number;
}

export const DrawingOverlay: React.FC<DrawingOverlayProps> = ({ videoRef, frameMap, videoFps }) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const { currentTime, clips } = useVideoPlayer();

  useEffect(() => {
    const canvas = canvasRef.current;
    const video = videoRef.current;
    if (!canvas || !video) return;

    canvas.width = video.clientWidth;
    canvas.height = video.clientHeight;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const currentFrame = Math.round(currentTime * videoFps);
    const scaleX = video.videoWidth > 0 ? canvas.width / video.videoWidth : 1;
    const scaleY = video.videoHeight > 0 ? canvas.height / video.videoHeight : 1;

    const activeClips = clips.filter(
      (clip) => currentTime >= clip.startTime && currentTime <= clip.endTime
    );

    for (const clip of activeClips) {
      for (const drawing of clip.drawings) {
        renderDrawing(ctx, drawing, canvas.width, canvas.height, currentFrame, frameMap, scaleX, scaleY);
      }
    }
  }, [currentTime, clips, frameMap, videoFps, videoRef]);

  return (
    <canvas
      ref={canvasRef}
      style={{
        position: 'absolute',
        top: 0,
        left: 0,
        width: '100%',
        height: '100%',
        pointerEvents: 'none',
      }}
    />
  );
};

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

function renderDrawing(
  ctx: CanvasRenderingContext2D,
  drawing: ClipDrawing,
  canvasWidth: number,
  canvasHeight: number,
  currentFrame: number,
  frameMap: TrackingFrameMap,
  scaleX: number,
  scaleY: number,
): void {
  switch (drawing.type) {
    case 'static': {
      const pixelPoints = drawing.points.map((p) => ({
        x: p.x * canvasWidth,
        y: p.y * canvasHeight,
      }));
      if (drawing.tool === 'pen') {
        drawPenPath(ctx, pixelPoints, drawing.color);
      } else if (drawing.tool === 'arrow' && pixelPoints.length >= 2) {
        drawArrow(ctx, pixelPoints[0], pixelPoints[pixelPoints.length - 1], drawing.color);
      } else if (drawing.tool === 'circle' && pixelPoints.length >= 2) {
        drawCircle(ctx, pixelPoints[0], pixelPoints[1], drawing.color);
      }
      break;
    }
    case 'anchored': {
      const result = findPlayerEntry(frameMap, currentFrame, drawing.playerId, TRACKING_GAP_TOLERANCE);
      if (!result) break;

      const { entry, gap } = result;
      const cx = ((entry.x1 + entry.x2) / 2) * scaleX;
      const cy = ((entry.y1 + entry.y2) / 2) * scaleY;

      const pixelPoints = drawing.points.map((p) => ({
        x: cx + p.dx * canvasWidth,
        y: cy + p.dy * canvasHeight,
      }));

      ctx.globalAlpha = gap > 0 ? 0.4 : 1.0;
      if (drawing.tool === 'pen') {
        drawPenPath(ctx, pixelPoints, drawing.color);
      } else if (drawing.tool === 'arrow' && pixelPoints.length >= 2) {
        drawArrow(ctx, pixelPoints[0], pixelPoints[pixelPoints.length - 1], drawing.color);
      } else if (drawing.tool === 'circle' && pixelPoints.length >= 2) {
        drawCircle(ctx, pixelPoints[0], pixelPoints[1], drawing.color);
      }
      ctx.globalAlpha = 1.0;
      break;
    }
  }
}
