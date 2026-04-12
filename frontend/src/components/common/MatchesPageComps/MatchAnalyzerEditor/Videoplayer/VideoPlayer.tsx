import React, { useRef, useEffect } from 'react';
import { Box } from '@mui/material';
import { useVideoPlayer } from '../../../../../context/VideoPlayerContext';
import { useHlsVideo } from '../../../../../hooks/VideoEdit/useHlsVideo';
import { useVideoDrawing } from '../../../../../hooks/VideoEdit/useVideoDrawing';

interface VideoPlayerProps {
  videoUrl: string;  
}

export const VideoPlayer: React.FC<VideoPlayerProps> = ({
  videoUrl
}) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const { currentTime, activeDrawTool } = useVideoPlayer();

  useHlsVideo(videoRef, videoUrl);
  useVideoDrawing(canvasRef, videoRef);  

  
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    if (Math.abs(video.currentTime - currentTime) > 0.1) {
      video.currentTime = currentTime;
    }
  }, [currentTime]);

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
      <canvas
        ref={canvasRef}
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          width: '100%',
          height: '100%',
          pointerEvents: activeDrawTool !== 'none' ? 'auto' : 'none',
          cursor: activeDrawTool !== 'none' ? 'crosshair' : 'default',
        }}
      />
    </Box>
  );
};
