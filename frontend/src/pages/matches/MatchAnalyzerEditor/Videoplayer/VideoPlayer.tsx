import React, { useRef } from 'react';
import { Box, CircularProgress } from '@mui/material';
import { HlsVideo } from './HlsVideo';
import { DrawingCanvas, type DrawingCanvasHandle } from './DrawingCanvas';
import type { BallFrameMap, TrackingFrameMap } from '../../hooks/VideoEdit/useTrackingData';
import type { TeamColors } from '../../../../utils/renderers/pitchRenderer';
import { useVideoBuffering } from '../../hooks/VideoEdit/useVideoBuffering';

interface VideoPlayerProps {
  videoUrl: string;
  frameMap: TrackingFrameMap;
  ballMap: BallFrameMap;
  videoFps: number;
  isHidden: boolean;
  show2DView: boolean;
  teamColors?: TeamColors;
}

export const VideoPlayer: React.FC<VideoPlayerProps> = ({
  videoUrl,
  videoFps,
  frameMap,
  ballMap,
  isHidden,
  show2DView,
  teamColors,
}) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const drawingCanvasRef = useRef<DrawingCanvasHandle>(null);
  const { isBuffering } = useVideoBuffering(videoRef);

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
      <HlsVideo ref={videoRef} videoUrl={videoUrl} />
      <DrawingCanvas
        ref={drawingCanvasRef}
        videoRef={videoRef}
        videoFps={videoFps}
        frameMap={frameMap}
        ballMap={ballMap}
        show2DView={show2DView}
        teamColors={teamColors}
      />
      {isBuffering && (
        <Box
          sx={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: 'rgba(0,0,0,0.35)',
            zIndex: 10,
            pointerEvents: 'none',
          }}
        >
          <CircularProgress sx={{ color: '#fff' }} size={48} />
        </Box>
      )}
    </Box>
  );
};
