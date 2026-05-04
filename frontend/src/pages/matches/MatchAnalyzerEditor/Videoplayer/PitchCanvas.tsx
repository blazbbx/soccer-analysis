import React, { forwardRef, useImperativeHandle, useRef, useEffect } from 'react';
import { useVideoPlayback } from '../../../../context/VideoPlayerContext';
import { useRecording } from '../../../../context/RecordingContext';
import { useRecordingPitchDrawing } from '../../hooks/VideoEdit/useRecordingPitchDrawing';
import { useRecordingPitchFollowDrawing } from '../../hooks/VideoEdit/useRecordingPitchFollowDrawing';
import { renderPitchFrame } from '../../../../utils/renderers/pitchRenderer';
import { renderStaticDrawings } from '../../../../utils/renderers/staticRenderer';
import { renderPitchAnchoredDrawings } from '../../../../utils/renderers/pitchAnchoredRenderer';
import type { StaticDrawing, AnchoredDrawing } from '../../../../types/drawings';
import type { TrackingFrameMap } from '../../hooks/VideoEdit/useTrackingData';

export interface PitchCanvasHandle {
  canvasElement: HTMLCanvasElement | null;
}

interface PitchCanvasProps {
  frameMap: TrackingFrameMap;
  videoFps: number;
}

// Fixed recording resolution matching 105:68 pitch aspect ratio
const REC_W = 1280;
const REC_H = Math.round(1280 * 68 / 105);

export const PitchCanvas = forwardRef<PitchCanvasHandle, PitchCanvasProps>(
  ({ frameMap, videoFps }, ref) => {
    const bgRef         = useRef<HTMLCanvasElement>(null);
    const staticRef     = useRef<HTMLCanvasElement>(null);
    const anchorRef     = useRef<HTMLCanvasElement>(null);
    const compositorRef = useRef<HTMLCanvasElement>(null);

    const { currentTime } = useVideoPlayback();
    const { isRecording, drawings } = useRecording();

    useRecordingPitchDrawing(staticRef);
    useRecordingPitchFollowDrawing(anchorRef, frameMap, videoFps);

    useImperativeHandle(ref, () => ({
      get canvasElement() { return compositorRef.current; },
    }));

    // Size all canvases once on mount
    useEffect(() => {
      for (const r of [bgRef, staticRef, anchorRef, compositorRef]) {
        const c = r.current;
        if (c) { c.width = REC_W; c.height = REC_H; }
      }
    }, []);

    // Redraw pitch background whenever video position changes
    useEffect(() => {
      const canvas = bgRef.current;
      if (!canvas) return;
      const ctx = canvas.getContext('2d');
      if (!ctx) return;
      const currentFrame = videoFps > 0 ? Math.round(currentTime * videoFps) + 1 : 1;
      const entries = frameMap.get(currentFrame) ?? [];
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      renderPitchFrame(ctx, canvas.width, canvas.height, entries);
    }, [currentTime, videoFps, frameMap]);

    // Re-render pitch static drawings whenever drawings state changes
    const pitchStatic = drawings.filter(
      (d): d is StaticDrawing => d.type === 'static' && d.view === 'pitch'
    );
    useEffect(() => {
      const canvas = staticRef.current;
      const ctx = canvas?.getContext('2d');
      if (!ctx || !canvas) return;
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      if (pitchStatic.length > 0) {
        renderStaticDrawings(ctx, pitchStatic, canvas.width, canvas.height);
      }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [drawings]);

    // Re-render pitch anchored drawings on every frame change so they track the player
    const pitchAnchored = drawings.filter(
      (d): d is AnchoredDrawing => d.type === 'anchored' && d.view === 'pitch'
    );
    useEffect(() => {
      const canvas = anchorRef.current;
      const ctx = canvas?.getContext('2d');
      if (!ctx || !canvas) return;
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      if (pitchAnchored.length > 0) {
        const currentFrame = videoFps > 0 ? Math.round(currentTime * videoFps) + 1 : 1;
        renderPitchAnchoredDrawings(ctx, pitchAnchored, canvas.width, canvas.height, currentFrame, frameMap);
      }
    }, [drawings, currentTime, videoFps, frameMap]);

    // Composite all layers into the exposed canvas while recording
    useEffect(() => {
      if (!isRecording) return;
      let rafId: number;
      const loop = () => {
        const comp   = compositorRef.current;
        const bg     = bgRef.current;
        const st     = staticRef.current;
        const an     = anchorRef.current;
        if (comp && bg && st && an) {
          const ctx = comp.getContext('2d');
          if (ctx) {
            ctx.clearRect(0, 0, comp.width, comp.height);
            ctx.drawImage(bg, 0, 0);
            ctx.drawImage(st, 0, 0);
            ctx.drawImage(an, 0, 0);
          }
        }
        rafId = requestAnimationFrame(loop);
      };
      rafId = requestAnimationFrame(loop);
      return () => cancelAnimationFrame(rafId);
    }, [isRecording]);

    const hidden: React.CSSProperties = { display: 'none' };
    return (
      <>
        <canvas ref={bgRef}         style={hidden} />
        <canvas ref={staticRef}     style={hidden} />
        <canvas ref={anchorRef}     style={hidden} />
        <canvas ref={compositorRef} style={hidden} />
      </>
    );
  }
);
