import React, { useRef, useEffect } from 'react';
import { useVideoPlayer } from '../../../../context/VideoPlayerContext';
import { useClip } from '../../../../context/ClipContext';
import type { StaticDrawing, AnchoredDrawing } from '../../../../types/drawings';
import type { TrackingFrameMap } from '../../hooks/VideoEdit/useTrackingData';
import { renderStaticDrawings } from '../../../../utils/renderers/staticRenderer';
import { renderAnchoredDrawings } from '../../../../utils/renderers/anchoredRenderer';

interface DrawingOverlayProps {
  videoRef: React.RefObject<HTMLVideoElement | null>;
  frameMap: TrackingFrameMap;
  videoFps: number;
}

export const DrawingOverlay: React.FC<DrawingOverlayProps> = ({ videoRef, frameMap, videoFps }) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const { currentTime } = useVideoPlayer();
  const { clips } = useClip();

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

    const staticDrawings = activeClips.flatMap((c) =>
      c.drawings.filter((d): d is StaticDrawing => d.type === 'static' && d.view === 'video')
    );
    const anchoredDrawings = activeClips.flatMap((c) =>
      c.drawings.filter((d): d is AnchoredDrawing => d.type === 'anchored' && d.view === 'video')
    );

    if (staticDrawings.length > 0) {
      renderStaticDrawings(ctx, staticDrawings, canvas.width, canvas.height);
    }
    if (anchoredDrawings.length > 0) {
      renderAnchoredDrawings(ctx, anchoredDrawings, canvas.width, canvas.height, currentFrame, frameMap, scaleX, scaleY);
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
