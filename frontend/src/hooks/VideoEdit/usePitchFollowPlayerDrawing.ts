import { useCallback, useEffect } from 'react';
import { useVideoPlayer } from '../../context/VideoPlayerContext';
import { useClip } from '../../context/ClipContext';
import type { TrackingFrameMap } from './useTrackingData';
import type { Point } from '../../utils/canvasDrawing';
import { useCanvasDrawing } from './useCanvasDrawing';
import { pitchToCanvas } from '../../utils/renderers/pitchAnchoredRenderer';

const PLAYER_CIRCLE_COLORS = [
  '#f44336', '#ff9800', '#ffeb3b', '#4caf50',
  '#2196f3', '#9c27b0', '#00bcd4', '#ff5722',
];

// SVG viewBox width used to compute pixel radius from SVG unit radius
const VB_W = 111;
const PLAYER_SVG_RADIUS = 1.6;

function getNearbyEntries(frameMap: TrackingFrameMap, frame: number) {
  let entries = frameMap.get(frame) ?? [];
  if (entries.length === 0) {
    for (let offset = 1; offset <= 5; offset++) {
      entries = frameMap.get(frame + offset) ?? frameMap.get(frame - offset) ?? [];
      if (entries.length > 0) break;
    }
  }
  return entries;
}

export const usePitchFollowPlayerDrawing = (
  anchorCanvasRef: React.RefObject<HTMLCanvasElement | null>,
  containerRef: React.RefObject<HTMLDivElement | null>,
  frameMap: TrackingFrameMap,
  videoFps: number,
): void => {
  const { isPlaying, currentTime } = useVideoPlayer();
  const {
    followPlayerMode,
    selectedPlayerId,
    setSelectedPlayerId,
    activeDrawTool,
    activeDrawColor,
    drawingClipId,
    addDrawingToClip,
    clips,
  } = useClip();

  // Sync anchor canvas size with container
  useEffect(() => {
    const canvas = anchorCanvasRef.current;
    const container = containerRef.current;
    if (!canvas || !container) return;

    const sync = () => {
      if (container.clientWidth > 0) {
        canvas.width = container.clientWidth;
        canvas.height = container.clientHeight;
      }
    };
    sync();
    const ro = new ResizeObserver(sync);
    ro.observe(container);
    return () => ro.disconnect();
  }, [anchorCanvasRef, containerRef]);

  // Draw player selection circles (no player selected) OR selected player ring
  useEffect(() => {
    if (!followPlayerMode || drawingClipId === null) return;

    const canvas = anchorCanvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const currentFrame = Math.round(currentTime * videoFps);
    const entries = getNearbyEntries(frameMap, currentFrame);
    const pixelRadius = (PLAYER_SVG_RADIUS / VB_W) * canvas.width;

    if (selectedPlayerId === null) {
      // Player selection circles only visible while paused
      if (isPlaying) return;
      entries.forEach((entry, idx) => {
        if (entry.tx == null || entry.ty == null) return;
        const { x: cx, y: cy } = pitchToCanvas(entry.tx, entry.ty, canvas.width, canvas.height);
        const color = PLAYER_CIRCLE_COLORS[idx % PLAYER_CIRCLE_COLORS.length];

        ctx.save();
        ctx.strokeStyle = color;
        ctx.lineWidth = 2.5;
        ctx.beginPath();
        ctx.arc(cx, cy, pixelRadius * 1.6, 0, Math.PI * 2);
        ctx.stroke();

        ctx.fillStyle = color;
        ctx.font = `bold ${Math.max(10, pixelRadius * 1.2)}px sans-serif`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'bottom';
        ctx.fillText(`#${entry.player_id}`, cx, cy - pixelRadius * 1.8);
        ctx.restore();
      });
    } else {
      // Dashed ring around selected player — always visible during clip editing
      const entry = entries.find((e) => e.player_id === selectedPlayerId);
      if (entry?.tx != null && entry?.ty != null) {
        const { x: cx, y: cy } = pitchToCanvas(entry.tx, entry.ty, canvas.width, canvas.height);

        ctx.save();
        ctx.strokeStyle = '#ffffff';
        ctx.lineWidth = 2;
        ctx.setLineDash([4, 3]);
        ctx.beginPath();
        ctx.arc(cx, cy, pixelRadius * 2.2, 0, Math.PI * 2);
        ctx.stroke();
        ctx.restore();
      }
    }
  }, [followPlayerMode, selectedPlayerId, drawingClipId, isPlaying, currentTime, frameMap, videoFps, anchorCanvasRef, clips]);

  // Click handler: select a player by clicking their circle
  useEffect(() => {
    if (!followPlayerMode || selectedPlayerId !== null || drawingClipId === null || isPlaying) return;

    const canvas = anchorCanvasRef.current;
    if (!canvas) return;

    const handleClick = (e: MouseEvent) => {
      const rect = canvas.getBoundingClientRect();
      const clickX = (e.clientX - rect.left) * (canvas.width / rect.width);
      const clickY = (e.clientY - rect.top) * (canvas.height / rect.height);

      const currentFrame = Math.round(currentTime * videoFps);
      const entries = getNearbyEntries(frameMap, currentFrame);
      const pixelRadius = (PLAYER_SVG_RADIUS / VB_W) * canvas.width;
      const hitRadius = Math.max(14, pixelRadius * 2);

      for (const entry of entries) {
        if (entry.tx == null || entry.ty == null) continue;
        const { x: cx, y: cy } = pitchToCanvas(entry.tx, entry.ty, canvas.width, canvas.height);
        const dist = Math.hypot(clickX - cx, clickY - cy);
        if (dist <= hitRadius) {
          setSelectedPlayerId(entry.player_id);
          return;
        }
      }
    };

    canvas.addEventListener('mousedown', handleClick);
    return () => canvas.removeEventListener('mousedown', handleClick);
  }, [followPlayerMode, selectedPlayerId, drawingClipId, isPlaying, currentTime, frameMap, videoFps, setSelectedPlayerId, anchorCanvasRef]);

  const onComplete = useCallback((rawPoints: Point[], canvas: HTMLCanvasElement) => {
    if (!drawingClipId || selectedPlayerId === null) return;

    const currentFrame = Math.round(currentTime * videoFps);
    const entries = getNearbyEntries(frameMap, currentFrame);
    const entry = entries.find((e) => e.player_id === selectedPlayerId);
    if (entry?.tx == null || entry?.ty == null) return;

    const { x: cx, y: cy } = pitchToCanvas(entry.tx, entry.ty, canvas.width, canvas.height);

    addDrawingToClip(drawingClipId, {
      id: `pitch-anchored-${Date.now()}`,
      type: 'anchored',
      view: 'pitch',
      playerId: selectedPlayerId,
      anchorFrame: currentFrame,
      tool: activeDrawTool === 'none' ? 'pen' : activeDrawTool,
      color: activeDrawColor,
      points: rawPoints.map((p) => ({
        dx: (p.x - cx) / canvas.width,
        dy: (p.y - cy) / canvas.height,
      })),
    });

    const ctx = canvas.getContext('2d');
    if (ctx) ctx.clearRect(0, 0, canvas.width, canvas.height);
  }, [drawingClipId, selectedPlayerId, currentTime, videoFps, frameMap, activeDrawTool, activeDrawColor, addDrawingToClip]);

  useCanvasDrawing({
    canvasRef: anchorCanvasRef,
    activeDrawTool,
    activeDrawColor,
    enabled: followPlayerMode && selectedPlayerId !== null && activeDrawTool !== 'none' && drawingClipId !== null,
    onComplete,
  });
};
