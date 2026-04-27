import React, { useRef, useEffect } from 'react';
import { Box } from '@mui/material';
import { useVideoPlayer } from '../../../../../context/VideoPlayerContext';
import { useHlsVideo } from '../../../../../hooks/VideoEdit/useHlsVideo';
import { useCanvasCompositor } from '../../../../../hooks/VideoEdit/useCanvasCompositor';
import type { TrackingFrameMap } from '../../../../../hooks/VideoEdit/useTrackingData';

interface VideoPlayerProps {
  videoUrl: string;
  frameMap: TrackingFrameMap;
  videoFps: number;
  isHidden: boolean;
}

export const VideoPlayer: React.FC<VideoPlayerProps> = ({
  videoUrl,
  isHidden,
}) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);

  const { currentTime } = useVideoPlayer();
  const { lastSyncedTimeRef } = useHlsVideo(videoRef, videoUrl);

  useCanvasCompositor(videoRef, canvasRef);

  // Apply external seeks (timeline scrub, skip buttons).
  // Skip if currentTime came from this video's own timeupdate event.
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    if (Math.abs(currentTime - lastSyncedTimeRef.current) < 0.1) return;
    video.currentTime = currentTime;
  }, [currentTime]);

  return (
    <Box
      sx={{
        position: 'relative',
        width: '100%',
        aspectRatio: '16/9',
        bgcolor: '#000',
        overflow: 'hidden',
        display: isHidden ? 'none' : 'block',
      }}
    >
      {/*
        <video> sits underneath the canvas at full size.
        NOT display:none — the browser keeps decoding frames so ctx.drawImage()
        always gets the current GPU texture.
      */}
      {/* Native video is the display layer — zero-copy HW compositing, no canvas overhead */}
      <video
        ref={videoRef}
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          width: '100%',
          height: '100%',
          pointerEvents: 'none',
        }}
      />

      {/*
        Hidden compositor canvas — fed by useCanvasCompositor for MediaRecorder only.
        NOT the display layer: GPU readback from MSE video takes 20-30ms per frame,
        which would stutter the display. Native <video> above handles display instead.
      */}
      <canvas
        ref={canvasRef}
        style={{ display: 'none' }}
      />
    </Box>
  );
};
