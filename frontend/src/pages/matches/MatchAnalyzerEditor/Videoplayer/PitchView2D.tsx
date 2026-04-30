import React, { useRef, useEffect } from 'react';
import { Box } from '@mui/material';
import { useVideoPlayer } from '../../../../context/VideoPlayerContext';
import { useRecording } from '../../../../context/RecordingContext';
import type { TrackingFrameMap } from '../../hooks/VideoEdit/useTrackingData';
import { useRecordingPitchDrawing } from '../../hooks/VideoEdit/useRecordingPitchDrawing';
import { useRecordingPitchFollowDrawing } from '../../hooks/VideoEdit/useRecordingPitchFollowDrawing';
import { getNearbyEntries } from '../../hooks/VideoEdit/useFollowPlayerDrawing';
import { pitchToCanvas, renderPitchAnchoredDrawings } from '../../../../utils/renderers/pitchAnchoredRenderer';
import { renderStaticDrawings } from '../../../../utils/renderers/staticRenderer';
import type { StaticDrawing, AnchoredDrawing } from '../../../../types/drawings';

interface PitchView2DProps {
  frameMap: TrackingFrameMap;
  videoFps: number;
}

const PLAYER_COLORS = [
  '#e74c3c', '#3498db', '#2ecc71', '#f39c12', '#9b59b6',
  '#1abc9c', '#e67e22', '#34495e', '#e91e63', '#00bcd4',
  '#ff5722', '#8bc34a',
];

const PITCH_W = 105;
const PITCH_H = 68;
const PENALTY_DEPTH  = 16.5;
const PENALTY_WIDTH  = 40.32;
const GOAL_DEPTH     = 5.5;
const GOAL_WIDTH     = 18.32;
const GOAL_POST_DEPTH = 2.44;
const GOAL_POST_WIDTH = 7.32;
const CENTER_R       = 9.15;
const PENALTY_SPOT   = 11;
const CORNER_R       = 1;
const PLAYER_SVG_RADIUS = 1.6;
const VB_W = 111;

const PitchMarkings: React.FC = () => {
  const penaltyY  = (PITCH_H - PENALTY_WIDTH)  / 2;
  const goalAreaY = (PITCH_H - GOAL_WIDTH)      / 2;
  const goalPostY = (PITCH_H - GOAL_POST_WIDTH) / 2;
  const centerX   = PITCH_W / 2;
  const centerY   = PITCH_H / 2;

  return (
    <g stroke="rgba(255,255,255,0.85)" strokeWidth="0.4" fill="none">
      <rect x={0} y={0} width={PITCH_W} height={PITCH_H} />
      <line x1={centerX} y1={0} x2={centerX} y2={PITCH_H} />
      <circle cx={centerX} cy={centerY} r={CENTER_R} />
      <circle cx={centerX} cy={centerY} r={0.5} fill="rgba(255,255,255,0.85)" />
      <rect x={0} y={penaltyY} width={PENALTY_DEPTH} height={PENALTY_WIDTH} />
      <rect x={0} y={goalAreaY} width={GOAL_DEPTH} height={GOAL_WIDTH} />
      <circle cx={PENALTY_SPOT} cy={centerY} r={0.4} fill="rgba(255,255,255,0.85)" />
      <rect x={PITCH_W - PENALTY_DEPTH} y={penaltyY} width={PENALTY_DEPTH} height={PENALTY_WIDTH} />
      <rect x={PITCH_W - GOAL_DEPTH}    y={goalAreaY} width={GOAL_DEPTH}    height={GOAL_WIDTH} />
      <circle cx={PITCH_W - PENALTY_SPOT} cy={centerY} r={0.4} fill="rgba(255,255,255,0.85)" />
      <rect x={-GOAL_POST_DEPTH} y={goalPostY} width={GOAL_POST_DEPTH} height={GOAL_POST_WIDTH} strokeWidth={0.5} />
      <rect x={PITCH_W}          y={goalPostY} width={GOAL_POST_DEPTH} height={GOAL_POST_WIDTH} strokeWidth={0.5} />
      <path d={`M ${CORNER_R} 0 A ${CORNER_R} ${CORNER_R} 0 0 1 0 ${CORNER_R}`} />
      <path d={`M ${PITCH_W - CORNER_R} 0 A ${CORNER_R} ${CORNER_R} 0 0 0 ${PITCH_W} ${CORNER_R}`} />
      <path d={`M 0 ${PITCH_H - CORNER_R} A ${CORNER_R} ${CORNER_R} 0 0 0 ${CORNER_R} ${PITCH_H}`} />
      <path d={`M ${PITCH_W} ${PITCH_H - CORNER_R} A ${CORNER_R} ${CORNER_R} 0 0 1 ${PITCH_W - CORNER_R} ${PITCH_H}`} />
    </g>
  );
};

const PLAYER_CIRCLE_COLORS = [
  '#f44336', '#ff9800', '#ffeb3b', '#4caf50',
  '#2196f3', '#9c27b0', '#00bcd4', '#ff5722',
];

export const PitchView2D: React.FC<PitchView2DProps> = ({ frameMap, videoFps }) => {
  const { currentTime, isPlaying } = useVideoPlayer();
  const { isRecording, followPlayerMode, selectedPlayerId, setSelectedPlayerId, drawings, drawingsRef } = useRecording();

  const containerRef    = useRef<HTMLDivElement>(null);
  const drawCanvasRef   = useRef<HTMLCanvasElement>(null);
  const anchorCanvasRef = useRef<HTMLCanvasElement>(null);

  useRecordingPitchDrawing(drawCanvasRef);
  useRecordingPitchFollowDrawing(anchorCanvasRef, frameMap, videoFps);

  const currentFrame = videoFps > 0 ? Math.round(currentTime * videoFps) + 1 : 1;
  const entries = frameMap.get(currentFrame) ?? [];

  // Size both canvases to the container
  useEffect(() => {
    const draw   = drawCanvasRef.current;
    const anchor = anchorCanvasRef.current;
    const container = containerRef.current;
    if (!draw || !anchor || !container) return;

    const sync = () => {
      if (container.clientWidth === 0) return;
      draw.width   = anchor.width   = container.clientWidth;
      draw.height  = anchor.height  = container.clientHeight;

      const staticPitch = drawingsRef.current.filter(
        (d): d is StaticDrawing => d.type === 'static' && d.view === 'pitch'
      );
      const drawCtx = draw.getContext('2d');
      if (drawCtx && staticPitch.length > 0) {
        renderStaticDrawings(drawCtx, staticPitch, draw.width, draw.height);
      }

      const anchoredPitch = drawingsRef.current.filter(
        (d): d is AnchoredDrawing => d.type === 'anchored' && d.view === 'pitch'
      );
      const anchorCtx = anchor.getContext('2d');
      const frame = videoFps > 0 ? Math.round(currentTime * videoFps) + 1 : 1;
      if (anchorCtx && anchoredPitch.length > 0) {
        renderPitchAnchoredDrawings(anchorCtx, anchoredPitch, anchor.width, anchor.height, frame, frameMap);
      }
    };

    const ro = new ResizeObserver(sync);
    ro.observe(container);
    return () => ro.disconnect();
  }, []);

  // Unified anchor canvas: committed anchored drawings + player selection UI
  useEffect(() => {
    const canvas = anchorCanvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (!ctx || !canvas) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const pitchAnchored = drawings.filter(
      (d): d is AnchoredDrawing => d.type === 'anchored' && d.view === 'pitch'
    );
    if (pitchAnchored.length > 0) {
      renderPitchAnchoredDrawings(ctx, pitchAnchored, canvas.width, canvas.height, currentFrame, frameMap);
    }

    if (!isRecording || !followPlayerMode) return;

    const pixelRadius = (PLAYER_SVG_RADIUS / VB_W) * canvas.width;
    const frameEntries = getNearbyEntries(frameMap, currentFrame);

    if (selectedPlayerId === null && !isPlaying) {
      frameEntries.forEach((entry, idx) => {
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
    } else if (selectedPlayerId !== null) {
      const entry = frameEntries.find((e) => e.player_id === selectedPlayerId);
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
  }, [isRecording, followPlayerMode, selectedPlayerId, isPlaying, currentFrame, frameMap, drawings]);

  // Click-to-select a player
  useEffect(() => {
    if (!isRecording || !followPlayerMode || selectedPlayerId !== null || isPlaying) return;

    const canvas = anchorCanvasRef.current;
    if (!canvas) return;

    const handleClick = (e: MouseEvent) => {
      const rect = canvas.getBoundingClientRect();
      const clickX = (e.clientX - rect.left) * (canvas.width / rect.width);
      const clickY = (e.clientY - rect.top)  * (canvas.height / rect.height);
      const pixelRadius = (PLAYER_SVG_RADIUS / VB_W) * canvas.width;
      const hitRadius = Math.max(14, pixelRadius * 2);
      const frameEntries = getNearbyEntries(frameMap, currentFrame);

      for (const entry of frameEntries) {
        if (entry.tx == null || entry.ty == null) continue;
        const { x: cx, y: cy } = pitchToCanvas(entry.tx, entry.ty, canvas.width, canvas.height);
        if (Math.hypot(clickX - cx, clickY - cy) <= hitRadius) {
          setSelectedPlayerId(entry.player_id);
          return;
        }
      }
    };

    canvas.addEventListener('mousedown', handleClick);
    return () => canvas.removeEventListener('mousedown', handleClick);
  }, [isRecording, followPlayerMode, selectedPlayerId, isPlaying, currentFrame, frameMap, setSelectedPlayerId]);

  return (
    <Box
      ref={containerRef}
      sx={{
        position: 'relative',
        width: '100%',
        aspectRatio: `${PITCH_W} / ${PITCH_H}`,
        bgcolor: '#1a6b1a',
        overflow: 'hidden',
      }}
    >
      <svg
        viewBox={`-3 -2 ${PITCH_W + 6} ${PITCH_H + 4}`}
        width="100%"
        height="100%"
        style={{ display: 'block' }}
      >
        {Array.from({ length: 10 }, (_, i) => (
          <rect
            key={i}
            x={i * (PITCH_W / 10)}
            y={0}
            width={PITCH_W / 10}
            height={PITCH_H}
            fill={i % 2 === 0 ? '#1e7a1e' : '#1a6b1a'}
          />
        ))}
        <PitchMarkings />
        {entries.map((entry) => {
          if (entry.tx == null || entry.ty == null) return null;
          const color = PLAYER_COLORS[(entry.player_id - 1) % PLAYER_COLORS.length];
          return (
            <g key={entry.player_id}>
              <circle cx={entry.tx} cy={entry.ty} r={1.6} fill={color} stroke="white" strokeWidth={0.3} />
              <text
                x={entry.tx}
                y={entry.ty + 0.55}
                textAnchor="middle"
                dominantBaseline="middle"
                fontSize={1.6}
                fontWeight="bold"
                fill="white"
                style={{ userSelect: 'none', pointerEvents: 'none' }}
              >
                {entry.player_id}
              </text>
            </g>
          );
        })}
      </svg>

      {/* Static drawing layer */}
      <canvas
        ref={drawCanvasRef}
        style={{
          position: 'absolute', top: 0, left: 0, width: '100%', height: '100%',
          pointerEvents: isRecording && !followPlayerMode ? 'auto' : 'none',
          cursor: isRecording && !followPlayerMode ? 'crosshair' : 'default',
        }}
      />
      {/* Anchor layer — player selection circles + anchored drawing */}
      <canvas
        ref={anchorCanvasRef}
        style={{
          position: 'absolute', top: 0, left: 0, width: '100%', height: '100%',
          pointerEvents: isRecording && followPlayerMode ? 'auto' : 'none',
          cursor: isRecording && followPlayerMode ? 'crosshair' : 'default',
        }}
      />
    </Box>
  );
};
