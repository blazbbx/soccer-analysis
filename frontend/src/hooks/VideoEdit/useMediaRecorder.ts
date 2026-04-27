import { useEffect } from 'react';
import { useRecording } from '../../context/RecordingContext';

export function useMediaRecorder(
  canvasRef: React.RefObject<HTMLCanvasElement | null>,
  videoFps: number
): void {
  const { isRecording } = useRecording();

  useEffect(() => {
    if (!isRecording) return;

    const canvas = canvasRef.current;
    if (!canvas) return;

    const mimeType = MediaRecorder.isTypeSupported('video/webm;codecs=vp9')
      ? 'video/webm;codecs=vp9'
      : 'video/webm';

    const stream = canvas.captureStream(videoFps);
    const recorder = new MediaRecorder(stream, { mimeType });
    const chunks: Blob[] = [];

    recorder.ondataavailable = (e) => {
      if (e.data.size > 0) chunks.push(e.data);
    };

    recorder.onstop = () => {
      const blob = new Blob(chunks, { type: 'video/webm' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `recording-${Date.now()}.webm`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    };

    recorder.start(1000);

    return () => {
      if (recorder.state !== 'inactive') {
        recorder.stop();
      }
    };
  }, [isRecording, canvasRef, videoFps]);
}