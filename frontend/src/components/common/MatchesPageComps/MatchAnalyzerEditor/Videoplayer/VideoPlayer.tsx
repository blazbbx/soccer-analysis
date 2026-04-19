import React, { useRef, useEffect, useMemo } from 'react';
import { Box } from '@mui/material';
import { useVideoPlayer } from '../../../../../context/VideoPlayerContext';
import { useHlsVideo } from '../../../../../hooks/VideoEdit/useHlsVideo';
import { useVideoDrawing } from '../../../../../hooks/VideoEdit/useVideoDrawing';
import { useFollowPlayerDrawing } from '../../../../../hooks/VideoEdit/useFollowPlayerDrawing';
import { useAnchoredDrawingRenderer } from '../../../../../hooks/VideoEdit/useAnchoredDrawingRenderer';
import type { TrackingFrameMap } from '../../../../../hooks/VideoEdit/useTrackingData';
import type { AnchoredDrawing } from '../../../../../types/anchoredDrawing';
import { DrawingOverlay } from './DrawingOverlay';

interface VideoPlayerProps {
  videoUrl: string;
  isEditor: boolean;
  frameMap: TrackingFrameMap;
  videoFps: number;
}

export const VideoPlayer: React.FC<VideoPlayerProps> = ({
  videoUrl,
  isEditor,
  frameMap,
  videoFps,
}) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const anchorCanvasRef = useRef<HTMLCanvasElement>(null);

  const { currentTime, activeDrawTool, followPlayerMode, selectedPlayerId, clips, drawingClipId, addDrawingToClip } = useVideoPlayer();

  const activeClip = useMemo(
    () => clips.find(c => currentTime >= c.startTime && currentTime <= c.endTime),
    [clips, currentTime]
  );

  const anchoredDrawingsRef = useRef<AnchoredDrawing[]>([]);
  anchoredDrawingsRef.current = (activeClip?.drawings ?? []).filter(
    (d): d is AnchoredDrawing => d.type === 'anchored'
  );

  const addDrawingToActiveClip = (drawing: AnchoredDrawing) => {
    if (drawingClipId) addDrawingToClip(drawingClipId, drawing);
  };

  // Clear the freehand canvas whenever the active clip changes (including leaving a clip)
  useEffect(() => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx) return;
    ctx.clearRect(0, 0, canvas.width, canvas.height);
  }, [activeClip?.id]);

  useHlsVideo(videoRef, videoUrl);
  useVideoDrawing(canvasRef, videoRef);
  useAnchoredDrawingRenderer(anchorCanvasRef, videoRef, frameMap, videoFps, anchoredDrawingsRef);
  useFollowPlayerDrawing(anchorCanvasRef, videoRef, frameMap, videoFps, addDrawingToActiveClip);

  // Sync video position to context time
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    if (Math.abs(video.currentTime - currentTime) > 0.1) {
      video.currentTime = currentTime;
    }
  }, [currentTime]);

  // Resize anchor canvas alongside the video
  useEffect(() => {
    const canvas = anchorCanvasRef.current;
    const video = videoRef.current;
    if (!canvas || !video) return;

    const resizeCanvas = () => {
      canvas.width = video.clientWidth;
      canvas.height = video.clientHeight;
    };

    resizeCanvas();
    window.addEventListener('resize', resizeCanvas);
    return () => window.removeEventListener('resize', resizeCanvas);
  }, []);

  const anchorCanvasCursor = drawingClipId !== null && followPlayerMode
    ? (selectedPlayerId !== null && activeDrawTool !== 'none' ? 'crosshair' : 'pointer')
    : 'default';

  return (
    <Box sx={{ position: 'relative', width: '100%', aspectRatio: '16/9', bgcolor: '#000' }}>
      <video
        ref={videoRef}
        style={{
          width: '100%',
          height: '100%',
          display: 'block',
        }}
      />
      {isEditor && (
        <>
          <canvas
            ref={canvasRef}
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              width: '100%',
              height: '100%',
              pointerEvents: drawingClipId !== null && activeDrawTool !== 'none' && !followPlayerMode ? 'auto' : 'none',
              cursor: drawingClipId !== null && activeDrawTool !== 'none' && !followPlayerMode ? 'crosshair' : 'default',
            }}
          />
          <canvas
            ref={anchorCanvasRef}
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              width: '100%',
              height: '100%',
              pointerEvents: drawingClipId !== null && followPlayerMode ? 'auto' : 'none',
              cursor: anchorCanvasCursor,
            }}
          />
          <DrawingOverlay videoRef={videoRef} frameMap={frameMap} videoFps={videoFps} />
        </>
      )}
    </Box>
  );
};
