import React, { useRef, useEffect } from 'react';
import { Box } from '@mui/material';
import { useVideoPlayer } from '../../../../../context/VideoPlayerContext';
import { useClip } from '../../../../../context/ClipContext';
import type { TrackingFrameMap } from '../../../../../hooks/VideoEdit/useTrackingData';
import type { StaticDrawing, AnchoredDrawing } from '../../../../../types/drawings';
import { renderStaticDrawings } from '../../../../../utils/renderers/staticRenderer';
import { renderPitchAnchoredDrawings } from '../../../../../utils/renderers/pitchAnchoredRenderer';
import { usePitchDrawing } from '../../../../../hooks/VideoEdit/usePitchDrawing';
import { usePitchFollowPlayerDrawing } from '../../../../../hooks/VideoEdit/usePitchFollowPlayerDrawing';

interface PitchView2DProps {
  frameMap: TrackingFrameMap;
  videoFps: number;
  isEditor: boolean;
}

const PLAYER_COLORS = [
  '#e74c3c', '#3498db', '#2ecc71', '#f39c12', '#9b59b6',
  '#1abc9c', '#e67e22', '#34495e', '#e91e63', '#00bcd4',
  '#ff5722', '#8bc34a',
];

// Standard pitch field markings (metres)
const PITCH_W = 105;
const PITCH_H = 68;
const PENALTY_DEPTH = 16.5;
const PENALTY_WIDTH = 40.32;
const GOAL_DEPTH = 5.5;
const GOAL_WIDTH = 18.32;
const GOAL_POST_DEPTH = 2.44;
const GOAL_POST_WIDTH = 7.32;
const CENTER_R = 9.15;
const PENALTY_SPOT = 11;
const CORNER_R = 1;

const PitchMarkings: React.FC = () => {
  const penaltyY   = (PITCH_H - PENALTY_WIDTH) / 2;
  const goalAreaY  = (PITCH_H - GOAL_WIDTH) / 2;
  const goalPostY  = (PITCH_H - GOAL_POST_WIDTH) / 2;
  const centerX = PITCH_W / 2;
  const centerY = PITCH_H / 2;

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
      <rect x={PITCH_W - GOAL_DEPTH} y={goalAreaY} width={GOAL_DEPTH} height={GOAL_WIDTH} />
      <circle cx={PITCH_W - PENALTY_SPOT} cy={centerY} r={0.4} fill="rgba(255,255,255,0.85)" />
      <rect x={-GOAL_POST_DEPTH} y={goalPostY} width={GOAL_POST_DEPTH} height={GOAL_POST_WIDTH} strokeWidth={0.5} />
      <rect x={PITCH_W} y={goalPostY} width={GOAL_POST_DEPTH} height={GOAL_POST_WIDTH} strokeWidth={0.5} />
      <path d={`M ${CORNER_R} 0 A ${CORNER_R} ${CORNER_R} 0 0 1 0 ${CORNER_R}`} />
      <path d={`M ${PITCH_W - CORNER_R} 0 A ${CORNER_R} ${CORNER_R} 0 0 0 ${PITCH_W} ${CORNER_R}`} />
      <path d={`M 0 ${PITCH_H - CORNER_R} A ${CORNER_R} ${CORNER_R} 0 0 0 ${CORNER_R} ${PITCH_H}`} />
      <path d={`M ${PITCH_W} ${PITCH_H - CORNER_R} A ${CORNER_R} ${CORNER_R} 0 0 1 ${PITCH_W - CORNER_R} ${PITCH_H}`} />
    </g>
  );
};

export const PitchView2D: React.FC<PitchView2DProps> = ({ frameMap, videoFps, isEditor }) => {
  const { currentTime } = useVideoPlayer();
  const { clips, activeDrawTool, drawingClipId, followPlayerMode } = useClip();

  const containerRef = useRef<HTMLDivElement>(null);
  const drawCanvasRef = useRef<HTMLCanvasElement>(null);
  const anchorCanvasRef = useRef<HTMLCanvasElement>(null);
  const savedCanvasRef = useRef<HTMLCanvasElement>(null);

  usePitchDrawing(drawCanvasRef, containerRef);
  usePitchFollowPlayerDrawing(anchorCanvasRef, containerRef, frameMap, videoFps);

  const currentFrame = videoFps > 0 ? Math.round(currentTime * videoFps) + 1 : 1;
  const entries = frameMap.get(currentFrame) ?? [];

  // Render committed pitch drawings (static + anchored) on the saved canvas
  useEffect(() => {
    const canvas = savedCanvasRef.current;
    const container = containerRef.current;
    if (!canvas || !container || container.clientWidth === 0) return;

    canvas.width = container.clientWidth;
    canvas.height = container.clientHeight;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    const activeClips = clips.filter(
      (c) => currentTime >= c.startTime && currentTime <= c.endTime
    );

    const pitchStaticDrawings = activeClips.flatMap((c) =>
      c.drawings.filter((d): d is StaticDrawing => d.type === 'static' && d.view === 'pitch')
    );
    const pitchAnchoredDrawings = activeClips.flatMap((c) =>
      c.drawings.filter((d): d is AnchoredDrawing => d.type === 'anchored' && d.view === 'pitch')
    );

    if (pitchStaticDrawings.length > 0) {
      renderStaticDrawings(ctx, pitchStaticDrawings, canvas.width, canvas.height);
    }
    if (pitchAnchoredDrawings.length > 0) {
      renderPitchAnchoredDrawings(ctx, pitchAnchoredDrawings, canvas.width, canvas.height, currentFrame, frameMap);
    }
  }, [currentTime, clips, currentFrame, frameMap]);

  const staticDrawingActive = isEditor && drawingClipId !== null && activeDrawTool !== 'none' && !followPlayerMode;
  const anchorDrawingActive = isEditor && drawingClipId !== null && followPlayerMode;

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
              <circle
                cx={entry.tx}
                cy={entry.ty}
                r={1.6}
                fill={color}
                stroke="white"
                strokeWidth={0.3}
              />
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

      {isEditor && (
        <>
          {/* Committed drawings overlay */}
          <canvas
            ref={savedCanvasRef}
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              width: '100%',
              height: '100%',
              pointerEvents: 'none',
            }}
          />
          {/* Static freehand drawing canvas */}
          <canvas
            ref={drawCanvasRef}
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              width: '100%',
              height: '100%',
              pointerEvents: staticDrawingActive ? 'auto' : 'none',
              cursor: staticDrawingActive ? 'crosshair' : 'default',
            }}
          />
          {/* Follow-player selection overlay and anchored drawing canvas */}
          <canvas
            ref={anchorCanvasRef}
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              width: '100%',
              height: '100%',
              pointerEvents: anchorDrawingActive ? 'auto' : 'none',
              cursor: anchorDrawingActive
                ? (followPlayerMode && isEditor ? 'pointer' : 'crosshair')
                : 'default',
            }}
          />
        </>
      )}
    </Box>
  );
};
