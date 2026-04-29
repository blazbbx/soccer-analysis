import { useEffect } from 'react';
import { useRecording } from '../../context/RecordingContext';
import { saveRecord } from '../../services/recordingService';

export function useMediaRecorder(
  canvasRef: React.RefObject<HTMLCanvasElement | null>,
  videoFps: number
): void {
  const { isRecording, recordDataRef, selectedMicId } = useRecording();

  useEffect(() => {
    if (!isRecording) return;

    const canvas = canvasRef.current;
    if (!canvas) return;

    const hasAudio = selectedMicId !== null;

    const mimeType = (() => {
      if (hasAudio && MediaRecorder.isTypeSupported('video/webm;codecs=vp9,opus')) return 'video/webm;codecs=vp9,opus';
      if (MediaRecorder.isTypeSupported('video/webm;codecs=vp9')) return 'video/webm;codecs=vp9';
      return 'video/webm';
    })();

    const stream = canvas.captureStream(videoFps);
    const chunks: Blob[] = [];
    let audioTracks: MediaStreamTrack[] = [];

    const startRecorder = (combinedStream: MediaStream) => {
      const recorder = new MediaRecorder(combinedStream, { mimeType });

      recorder.ondataavailable = (e) => {
        if (e.data.size > 0) chunks.push(e.data);
      };

      recorder.onstop = () => {
        audioTracks.forEach((t) => t.stop());
        const blob = new Blob(chunks, { type: 'video/webm' });
        // recordDataRef.current is always up-to-date regardless of React render timing
        saveRecord([...recordDataRef.current], blob);
      };

      recorder.start(1000);

      return recorder;
    };

    let recorder: MediaRecorder | null = null;

    if (hasAudio) {
      const audioConstraint = selectedMicId === '__default__'
        ? { audio: true }
        : { audio: { deviceId: { exact: selectedMicId } } };

      navigator.mediaDevices
        .getUserMedia(audioConstraint)
        .then((audioStream) => {
          audioTracks = audioStream.getAudioTracks();
          audioTracks.forEach((t) => stream.addTrack(t));
          window.dispatchEvent(new Event('micpermissiongranted'));
          recorder = startRecorder(stream);
        })
        .catch(() => {
          recorder = startRecorder(stream);
        });
    } else {
      recorder = startRecorder(stream);
    }

    return () => {
      if (recorder && recorder.state !== 'inactive') recorder.stop();
      else audioTracks.forEach((t) => t.stop());
    };
  }, [isRecording, canvasRef, videoFps, recordDataRef, selectedMicId]);
}
