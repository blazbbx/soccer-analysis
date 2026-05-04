import React, { forwardRef, useImperativeHandle, useRef, useEffect } from 'react';
import { useVideoPlayback, useVideoPlayer } from '../../../../context/VideoPlayerContext';
import { useRecording } from '../../../../context/RecordingContext';
import {
  useFollowPlayerDrawing,
  getScaleFactor,
  getNearbyEntries,
  PLAYER_BOX_COLORS,
} from '../../hooks/VideoEdit/useFollowPlayerDrawing';
import { useAnchoredDrawingRenderer } from '../../hooks/VideoEdit/useAnchoredDrawingRenderer';
import type { AnchoredDrawing } from '../../../../types/drawings';
import type { TrackingFrameMap } from '../../hooks/VideoEdit/useTrackingData';

export interface AnchorCanvasHandle {
  clear: () => void;
  canvasElement: HTMLCanvasElement | null;
}

interface AnchorCanvasProps {
  videoRef: React.RefObject<HTMLVideoElement | null>;
  videoFps: number;
  frameMap: TrackingFrameMap;
}

export const AnchorCanvas = forwardRef<AnchorCanvasHandle, AnchorCanvasProps>(
  ({ videoRef, videoFps, frameMap }, ref) => {
    const canvasRef = useRef<HTMLCanvasElement>(null);

    const { currentTime } = useVideoPlayback();
    const { isPlaying } = useVideoPlayer();
    const {
      isRecording,
      followPlayerMode,
      selectedPlayerId,
      setSelectedPlayerId,
      drawings,
    } = useRecording();

    const anchoredDrawings = drawings.filter((d): d is AnchoredDrawing => d.type === 'anchored' && d.view === 'video');

    useFollowPlayerDrawing(canvasRef, videoRef, frameMap, videoFps);
    useAnchoredDrawingRenderer(canvasRef, videoRef, frameMap, videoFps, anchoredDrawings);

    useImperativeHandle(ref, () => ({
      clear: () => {
        const canvas = canvasRef.current;
        const ctx = canvas?.getContext('2d');
        if (ctx && canvas) ctx.clearRect(0, 0, canvas.width, canvas.height);
      },
      get canvasElement() { return canvasRef.current; },
    }));

    // Size canvas to video display dimensions
    useEffect(() => {
      const video = videoRef.current;
      const canvas = canvasRef.current;
      if (!video || !canvas) return;

      const sync = () => {
        if (video.clientWidth === 0) return;
        canvas.width = video.clientWidth;
        canvas.height = video.clientHeight;
      };

      const observer = new ResizeObserver(sync);
      observer.observe(video);
      return () => observer.disconnect();
    }, [videoRef]);

    // Render player bounding boxes for selection (paused, no player chosen yet)
    useEffect(() => {
      if (!followPlayerMode || selectedPlayerId !== null || !isRecording || isPlaying) return;

      const canvas = canvasRef.current;
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
    }, [isPlaying, followPlayerMode, selectedPlayerId, isRecording, currentTime, frameMap, videoFps, videoRef]);

    // Click-to-select a player
    useEffect(() => {
      if (!followPlayerMode || selectedPlayerId !== null || !isRecording || isPlaying) return;

      const canvas = canvasRef.current;
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
    }, [isPlaying, followPlayerMode, selectedPlayerId, isRecording, currentTime, frameMap, videoFps, setSelectedPlayerId, videoRef]);

    return (
      <canvas
        ref={canvasRef}
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          width: '100%',
          height: '100%',
          pointerEvents: isRecording && followPlayerMode ? 'auto' : 'none',
          cursor: isRecording && followPlayerMode ? 'crosshair' : 'default',
        }}
      />
    );
  }
);
