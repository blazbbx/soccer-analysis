import React, { useRef, useEffect, useLayoutEffect, useMemo, useState } from 'react';
import { Box } from '@mui/material';
import { useVideoPlayer } from '../../../../../context/VideoPlayerContext';
import { useClip } from '../../../../../context/ClipContext';
import { useHlsVideo } from '../../../../../hooks/VideoEdit/useHlsVideo';
import { useVideoDrawing } from '../../../../../hooks/VideoEdit/useVideoDrawing';
import { useFollowPlayerDrawing } from '../../../../../hooks/VideoEdit/useFollowPlayerDrawing';
import { useAnchoredDrawingRenderer } from '../../../../../hooks/VideoEdit/useAnchoredDrawingRenderer';
import type { TrackingFrameMap } from '../../../../../hooks/VideoEdit/useTrackingData';
import type { AnchoredDrawing } from '../../../../../types/drawings';
import { DrawingOverlay } from './DrawingOverlay';

interface VideoPlayerProps {
  videoUrl: string;
  isEditor: boolean;
  frameMap: TrackingFrameMap;
  videoFps: number;
  isHidden: boolean;
}

export const VideoPlayer: React.FC<VideoPlayerProps> = ({
  videoUrl,
  isEditor,
  frameMap,
  videoFps,
  isHidden,
}) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const anchorCanvasRef = useRef<HTMLCanvasElement>(null);
  const [seekPending, setSeekPending] = useState(false);

  const { currentTime } = useVideoPlayer();
  const { activeDrawTool, followPlayerMode, selectedPlayerId, clips, drawingClipId, addDrawingToClip } = useClip();

  const editingClip = drawingClipId ? clips.find(c => c.id === drawingClipId) : null;

  const anchoredVideoDrawings = useMemo(
    () => (editingClip?.drawings ?? []).filter(
      (d): d is AnchoredDrawing => d.type === 'anchored' && d.view === 'video'
    ),
    [editingClip?.drawings]
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
  }, [editingClip?.id]);

  useHlsVideo(videoRef, videoUrl);
  useVideoDrawing(canvasRef, videoRef);
  useAnchoredDrawingRenderer(anchorCanvasRef, videoRef, frameMap, videoFps, anchoredVideoDrawings);
  useFollowPlayerDrawing(anchorCanvasRef, videoRef, frameMap, videoFps, addDrawingToActiveClip);

  // Sync video position to context time
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    if (Math.abs(video.currentTime - currentTime) > 0.1) {
      video.currentTime = currentTime;
    }
  }, [currentTime]);

  // useLayoutEffect runs before the browser paints, so seekPending=true hides the video
  // before the first frame is drawn — preventing the red-frame flash.
  useLayoutEffect(() => {
    if (isHidden) return;
    const video = videoRef.current;
    if (!video) return;

    setSeekPending(true);

    video.currentTime = Math.max(0, video.currentTime - 0.001); 

    let rAFId1 = 0;
    let rAFId2 = 0;
    const handleSeeked = () => {
      // Wait two animation frames so the GPU has time to upload the decoded frame
      // before we reveal the video element.
      rAFId1 = requestAnimationFrame(() => {
        rAFId2 = requestAnimationFrame(() => setSeekPending(false));
      });
    };

    video.addEventListener('seeked', handleSeeked, { once: true });
    const timeout = setTimeout(() => setSeekPending(false), 50);

    return () => {
      video.removeEventListener('seeked', handleSeeked);
      clearTimeout(timeout);
      cancelAnimationFrame(rAFId1);
      cancelAnimationFrame(rAFId2);
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isHidden]);

  // Resize anchor canvas alongside the video (ResizeObserver handles display:none → visible transitions)
  useEffect(() => {
    const canvas = anchorCanvasRef.current;
    const video = videoRef.current;
    if (!canvas || !video) return;

    const resizeCanvas = () => {
      if (video.clientWidth > 0) {
        canvas.width = video.clientWidth;
        canvas.height = video.clientHeight;
      }
    };

    const observer = new ResizeObserver(resizeCanvas);
    observer.observe(video);
    return () => observer.disconnect();
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
          visibility: seekPending ? 'hidden' : 'visible',
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
              opacity: seekPending ? 0 : 1,
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
              opacity: seekPending ? 0 : 1,
            }}
          />
          {!seekPending && (
            <DrawingOverlay videoRef={videoRef} frameMap={frameMap} videoFps={videoFps} />
          )}
        </>
      )}
    </Box>
  );
};
