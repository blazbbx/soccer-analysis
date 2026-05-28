import { useEffect } from 'react';
import { useRecording } from '../../../../context/RecordingContext';

export function useMediaRecorder(
  canvasRef: React.RefObject<HTMLCanvasElement | null>,
  videoFps: number
): void {
  const { isRecording, recordDataRef, selectedMicId, setPendingRecording } = useRecording();

  useEffect(() => {
    if (!isRecording) return;

    const canvas = canvasRef.current;
    if (!canvas) return;

    const hasAudio = selectedMicId !== null;

    const overlayStream = canvas.captureStream(videoFps);
    const overlayChunks: Blob[] = [];
    const audioChunks: Blob[] = [];
    let audioTracks: MediaStreamTrack[] = [];

    let overlayDone = false;
    let audioDone = !hasAudio;
    let audioBlob: Blob | null = null;

    const tryComplete = () => {
      if (!overlayDone || !audioDone) return;
      const overlayBlob = new Blob(overlayChunks, { type: 'video/webm' });
      setPendingRecording({
        overlayBlob,
        audioBlob,
        syncData: [...recordDataRef.current],
      });
    };

    const overlayMimeType = MediaRecorder.isTypeSupported('video/webm;codecs=vp9')
      ? 'video/webm;codecs=vp9'
      : 'video/webm';

    const overlayRecorder = new MediaRecorder(overlayStream, { mimeType: overlayMimeType });
    overlayRecorder.ondataavailable = (e) => { if (e.data.size > 0) overlayChunks.push(e.data); };
    overlayRecorder.onstop = () => {
      overlayDone = true;
      tryComplete();
    };
    overlayRecorder.start(1000);

    let audioRecorder: MediaRecorder | null = null;

    if (hasAudio) {
      const audioConstraint = selectedMicId === '__default__'
        ? { audio: true }
        : { audio: { deviceId: { exact: selectedMicId } } };

      navigator.mediaDevices
        .getUserMedia(audioConstraint)
        .then((audioStream) => {
          audioTracks = audioStream.getAudioTracks();
          window.dispatchEvent(new Event('micpermissiongranted'));

          const audioMimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
            ? 'audio/webm;codecs=opus'
            : 'audio/webm';

          audioRecorder = new MediaRecorder(audioStream, { mimeType: audioMimeType });
          audioRecorder.ondataavailable = (e) => { if (e.data.size > 0) audioChunks.push(e.data); };
          audioRecorder.onstop = () => {
            audioTracks.forEach((t) => t.stop());
            audioBlob = new Blob(audioChunks, { type: 'audio/webm' });
            audioDone = true;
            tryComplete();
          };
          audioRecorder.start(1000);
        })
        .catch(() => {
          audioDone = true;
          tryComplete();
        });
    }

    return () => {
      if (overlayRecorder.state !== 'inactive') overlayRecorder.stop();
      if (audioRecorder && audioRecorder.state !== 'inactive') audioRecorder.stop();
      else audioTracks.forEach((t) => t.stop());
    };
  }, [isRecording, canvasRef, videoFps, recordDataRef, selectedMicId, setPendingRecording]);
}
