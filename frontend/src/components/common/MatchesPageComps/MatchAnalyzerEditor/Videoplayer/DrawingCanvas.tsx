import React, { forwardRef, useImperativeHandle, useRef, useEffect } from 'react';
import { useRecording } from '../../../../../context/RecordingContext';
import { useMediaRecorder } from '../../../../../hooks/VideoEdit/useMediaRecorder';
import { StaticCanvas, type StaticCanvasHandle } from './StaticCanvas';
import { AnchorCanvas, type AnchorCanvasHandle } from './AnchorCanvas';
import { PitchCanvas, type PitchCanvasHandle } from './PitchCanvas';
import type { TrackingFrameMap } from '../../../../../hooks/VideoEdit/useTrackingData';

export interface DrawingCanvasHandle {
  clear: () => void;
}

interface DrawingCanvasProps {
  videoRef: React.RefObject<HTMLVideoElement | null>;
  videoFps: number;
  frameMap: TrackingFrameMap;
  show2DView: boolean;
}

export const DrawingCanvas = forwardRef<DrawingCanvasHandle, DrawingCanvasProps>(
  ({ videoRef, videoFps, frameMap, show2DView }, ref) => {
    const staticRef    = useRef<StaticCanvasHandle>(null);
    const anchorRef    = useRef<AnchorCanvasHandle>(null);
    const pitchRef     = useRef<PitchCanvasHandle>(null);
    const compositorRef = useRef<HTMLCanvasElement>(null);

    const { isRecording } = useRecording();

    useMediaRecorder(compositorRef, videoFps);

    // Composite layers onto the hidden canvas while recording
    useEffect(() => {
      if (!isRecording) return;

      let rafId: number;
      const loop = () => {
        const compositor = compositorRef.current;
        if (!compositor) { rafId = requestAnimationFrame(loop); return; }

        const ctx = compositor.getContext('2d');
        if (!ctx) { rafId = requestAnimationFrame(loop); return; }

        const video = videoRef.current;
        const vw = video?.videoWidth ?? 0;
        const vh = video?.videoHeight ?? 0;

        if (show2DView) {
          const pitchEl = pitchRef.current?.canvasElement;
          if (pitchEl) {
            const w = vw || pitchEl.width;
            const h = vh || pitchEl.height;
            if (compositor.width !== w || compositor.height !== h) {
              compositor.width  = w;
              compositor.height = h;
            }
            ctx.clearRect(0, 0, compositor.width, compositor.height);
            ctx.drawImage(pitchEl, 0, 0, compositor.width, compositor.height);
          }
        } else {
          const staticEl = staticRef.current?.canvasElement;
          const anchorEl = anchorRef.current?.canvasElement;
          if (staticEl && anchorEl) {
            const w = vw || staticEl.width;
            const h = vh || staticEl.height;
            if (compositor.width !== w || compositor.height !== h) {
              compositor.width  = w;
              compositor.height = h;
            }
            ctx.fillStyle = '#FF00FF';
            ctx.fillRect(0, 0, compositor.width, compositor.height);
            ctx.drawImage(staticEl, 0, 0, compositor.width, compositor.height);
            ctx.drawImage(anchorEl, 0, 0, compositor.width, compositor.height);
          }
        }

        rafId = requestAnimationFrame(loop);
      };
      rafId = requestAnimationFrame(loop);
      return () => cancelAnimationFrame(rafId);
    }, [isRecording, show2DView]);

    useEffect(() => {
      if (isRecording) return;
      anchorRef.current?.clear();
    }, [isRecording]);

    useImperativeHandle(ref, () => ({
      clear: () => staticRef.current?.clear(),
    }));

    return (
      <>
        <StaticCanvas ref={staticRef} videoRef={videoRef} />
        <AnchorCanvas ref={anchorRef} videoRef={videoRef} videoFps={videoFps} frameMap={frameMap} />
        {show2DView && <PitchCanvas ref={pitchRef} frameMap={frameMap} videoFps={videoFps} />}
        <canvas ref={compositorRef} style={{ display: 'none' }} />
      </>
    );
  }
);
