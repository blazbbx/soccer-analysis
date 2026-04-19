import { useEffect, useRef } from 'react';
import { useVideoPlayer } from '../../context/VideoPlayerContext';
import type { TrackingFrameMap } from './useTrackingData';
import type { AnchoredDrawing } from '../../types/anchoredDrawing';
import { drawArrow, drawCircle, drawPenPath, type Point } from '../../utils/canvasDrawing';

const PLAYER_BOX_COLORS = [
  '#f44336', '#ff9800', '#ffeb3b', '#4caf50',
  '#2196f3', '#9c27b0', '#00bcd4', '#ff5722',
];

function getScaleFactor(video: HTMLVideoElement, canvas: HTMLCanvasElement) {
  return {
    scaleX: video.videoWidth > 0 ? canvas.width / video.videoWidth : 1,
    scaleY: video.videoHeight > 0 ? canvas.height / video.videoHeight : 1,
  };
}

function toRelativePoints(
  rawPoints: Point[],
  cx: number,
  cy: number,
  canvasWidth: number,
  canvasHeight: number
): AnchoredDrawing['points'] {
  return rawPoints.map((p) => ({
    dx: (p.x - cx) / canvasWidth,
    dy: (p.y - cy) / canvasHeight,
  }));
}

export const useFollowPlayerDrawing = (
  anchorCanvasRef: React.RefObject<HTMLCanvasElement | null>,
  videoRef: React.RefObject<HTMLVideoElement | null>,
  frameMap: TrackingFrameMap,
  videoFps: number,
  addAnchoredDrawing: (drawing: AnchoredDrawing) => void
): void => {
  const {
    followPlayerMode,
    selectedPlayerId,
    setSelectedPlayerId,
    activeDrawTool,
    activeDrawColor,
    currentTime,
  } = useVideoPlayer();

  const isDrawing = useRef(false);
  const startPos = useRef<Point>({ x: 0, y: 0 });
  const penPoints = useRef<Point[]>([]);
  const savedSnapshot = useRef<ImageData | null>(null);

  // Draw all player bounding boxes for the current frame (selection overlay)
  useEffect(() => {
    if (!followPlayerMode || selectedPlayerId !== null) return;

    const canvas = anchorCanvasRef.current;
    const video = videoRef.current;
    if (!canvas || !video) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const currentFrame = Math.round(currentTime * videoFps);
    // Fall back to nearest frame within ±5 if exact frame has no data
    let entries = frameMap.get(currentFrame) ?? [];
    if (entries.length === 0) {
      for (let offset = 1; offset <= 5; offset++) {
        entries = frameMap.get(currentFrame + offset) ?? frameMap.get(currentFrame - offset) ?? [];
        if (entries.length > 0) break;
      }
    }
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
  }, [followPlayerMode, selectedPlayerId, currentTime, frameMap, videoFps, anchorCanvasRef, videoRef]);

  // Handle mousedown for player selection
  useEffect(() => {
    if (!followPlayerMode || selectedPlayerId !== null) return;

    const canvas = anchorCanvasRef.current;
    const video = videoRef.current;
    if (!canvas || !video) return;

    const handleClick = (e: MouseEvent) => {
      const rect = canvas.getBoundingClientRect();
      const clickX = e.clientX - rect.left;
      const clickY = e.clientY - rect.top;

      const currentFrame = Math.round(currentTime * videoFps);
      let entries = frameMap.get(currentFrame) ?? [];
      if (entries.length === 0) {
        for (let offset = 1; offset <= 5; offset++) {
          entries = frameMap.get(currentFrame + offset) ?? frameMap.get(currentFrame - offset) ?? [];
          if (entries.length > 0) break;
        }
      }
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
  }, [followPlayerMode, selectedPlayerId, currentTime, frameMap, videoFps, setSelectedPlayerId, anchorCanvasRef, videoRef]);

  // Handle drawing capture when a player is selected
  useEffect(() => {
    if (!followPlayerMode || selectedPlayerId === null || activeDrawTool === 'none') return;

    const canvas = anchorCanvasRef.current;
    const video = videoRef.current;
    if (!canvas || !video) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const getMousePos = (evt: MouseEvent): Point => {
      const rect = canvas.getBoundingClientRect();
      return { x: evt.clientX - rect.left, y: evt.clientY - rect.top };
    };

    const handleMouseDown = (e: MouseEvent) => {
      isDrawing.current = true;
      startPos.current = getMousePos(e);
      penPoints.current = [startPos.current];
      savedSnapshot.current = ctx.getImageData(0, 0, canvas.width, canvas.height);

      ctx.strokeStyle = activeDrawColor;
      ctx.fillStyle = activeDrawColor;
      ctx.lineWidth = 3;
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';

      if (activeDrawTool === 'pen') {
        ctx.beginPath();
        ctx.moveTo(startPos.current.x, startPos.current.y);
      }
    };

    const handleMouseMove = (e: MouseEvent) => {
      if (!isDrawing.current) return;
      const currentPos = getMousePos(e);

      if (activeDrawTool === 'pen') {
        penPoints.current.push(currentPos);
        ctx.lineTo(currentPos.x, currentPos.y);
        ctx.stroke();
      } else {
        if (savedSnapshot.current) ctx.putImageData(savedSnapshot.current, 0, 0);

        if (activeDrawTool === 'arrow') {
          drawArrow(ctx, startPos.current, currentPos, activeDrawColor);
        } else if (activeDrawTool === 'circle') {
          drawCircle(ctx, startPos.current, currentPos, activeDrawColor);
        }
      }
    };

    const handleMouseUp = (e: MouseEvent) => {
      if (!isDrawing.current) return;
      isDrawing.current = false;

      const currentPos = getMousePos(e);
      const currentFrame = Math.round(currentTime * videoFps);
      const entries = frameMap.get(currentFrame) ?? [];
      const entry = entries.find((en) => en.player_id === selectedPlayerId);
      if (!entry) return;

      const { scaleX, scaleY } = getScaleFactor(video, canvas);
      const cx = ((entry.x1 + entry.x2) / 2) * scaleX;
      const cy = ((entry.y1 + entry.y2) / 2) * scaleY;

      let rawPoints: Point[];
      if (activeDrawTool === 'pen') {
        rawPoints = penPoints.current;
      } else {
        rawPoints = [startPos.current, currentPos];
      }

      const relativePoints = toRelativePoints(rawPoints, cx, cy, canvas.width, canvas.height);

      addAnchoredDrawing({
        id: `anchored-${Date.now()}`,
        type: 'anchored',
        playerId: selectedPlayerId,
        anchorFrame: currentFrame,
        tool: activeDrawTool,
        color: activeDrawColor,
        points: relativePoints,
      });
    };

    canvas.addEventListener('mousedown', handleMouseDown);
    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);

    return () => {
      canvas.removeEventListener('mousedown', handleMouseDown);
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [
    followPlayerMode,
    selectedPlayerId,
    activeDrawTool,
    activeDrawColor,
    currentTime,
    frameMap,
    videoFps,
    addAnchoredDrawing,
    anchorCanvasRef,
    videoRef,
  ]);

};
