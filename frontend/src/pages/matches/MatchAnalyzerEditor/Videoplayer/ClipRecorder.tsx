import { useEffect, useRef } from 'react';
import { PITCH_CANVAS } from '../../../../constants/pitch';
import { VIDEO_VIEW } from '../../../../constants/video';
import { useRecording } from '../../../../context/RecordingContext';
import { useViewMode } from '../../../../context/ViewModeContext';
import { useMediaRecorder } from '../../hooks/VideoEdit/useMediaRecorder';
import type { RecordingSurfaceHandle } from './recordingSurface';

interface ClipRecorderProps {
  videoSurface: React.RefObject<RecordingSurfaceHandle | null>;
  pitchSurface: React.RefObject<RecordingSurfaceHandle | null>;
  videoFps: number;
}

// A kimeneti keret mérete a felvétel alatt nem változik; a pályanézet arányosan illeszkedik bele.
const FRAME = VIDEO_VIEW;
// Ezt a színt a backend átlátszóvá kulcsolja (clip_renderer: colorkey 0xFF00FF).
const CHROMA_KEY = '#FF00FF';
const PITCH_FILL = '#1a6b1a';

// Felvétel közben az aktív nézet rétegeit rakja egy rejtett vászonra, amit a MediaRecorder rögzít.
export function ClipRecorder({ videoSurface, pitchSurface, videoFps }: ClipRecorderProps) {
  const compositorRef = useRef<HTMLCanvasElement>(null);
  const { isRecording } = useRecording();
  const { viewMode } = useViewMode();
  const viewModeRef = useRef(viewMode);

  useEffect(() => {
    viewModeRef.current = viewMode;
  }, [viewMode]);

  useMediaRecorder(compositorRef, videoFps);

  useEffect(() => {
    if (!isRecording) return;
    const ctx = compositorRef.current?.getContext('2d');
    if (!ctx) return;

    let frameHandle = 0;
    const compose = () => {
      if (viewModeRef.current === 'video') {
        const content = videoSurface.current?.contentCanvas;
        ctx.fillStyle = CHROMA_KEY;
        ctx.fillRect(0, 0, FRAME.width, FRAME.height);
        if (content) ctx.drawImage(content, 0, 0, FRAME.width, FRAME.height);
      } else {
        const scene = pitchSurface.current?.sceneCanvas;
        const content = pitchSurface.current?.contentCanvas;
        ctx.fillStyle = PITCH_FILL;
        ctx.fillRect(0, 0, FRAME.width, FRAME.height);

        const scale = Math.min(FRAME.width / PITCH_CANVAS.width, FRAME.height / PITCH_CANVAS.height);
        const w = PITCH_CANVAS.width * scale;
        const h = PITCH_CANVAS.height * scale;
        const x = (FRAME.width - w) / 2;
        const y = (FRAME.height - h) / 2;
        if (scene) ctx.drawImage(scene, x, y, w, h);
        if (content) ctx.drawImage(content, x, y, w, h);
      }
      frameHandle = requestAnimationFrame(compose);
    };
    frameHandle = requestAnimationFrame(compose);
    return () => cancelAnimationFrame(frameHandle);
  }, [isRecording, videoSurface, pitchSurface]);

  return <canvas ref={compositorRef} width={FRAME.width} height={FRAME.height} style={{ display: 'none' }} />;
}
