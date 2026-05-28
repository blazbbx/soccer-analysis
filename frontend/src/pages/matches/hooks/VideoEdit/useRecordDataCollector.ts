import { useEffect } from 'react';
import { useRecording } from '../../../../context/RecordingContext';
import { ClipSyncEventType } from '../../../../api/generated/model';

export function useRecordDataCollector(
  videoRef: React.RefObject<HTMLVideoElement | null>
): void {
  const { isRecording, addRecordEvent } = useRecording();

  useEffect(() => {
    if (!isRecording) return;
    const video = videoRef.current;
    if (!video) return;

    // Anchor the timeline at t≈0 with the current video state
    addRecordEvent(
      video.paused ? ClipSyncEventType.PAUSE : ClipSyncEventType.PLAY,
      video.currentTime
    );

    const handlePlay = () => addRecordEvent(ClipSyncEventType.PLAY, video.currentTime);
    const handlePause = () => addRecordEvent(ClipSyncEventType.PAUSE, video.currentTime);
    // 'seeked' fires once the seek is complete and currentTime is settled
    const handleSeeked = () => addRecordEvent(ClipSyncEventType.SEEK, video.currentTime);

    video.addEventListener('play', handlePlay);
    video.addEventListener('ratechange', handlePlay);
    video.addEventListener('pause', handlePause);
    video.addEventListener('seeked', handleSeeked);

    return () => {
      video.removeEventListener('play', handlePlay);
      video.removeEventListener('ratechange', handlePlay);
      video.removeEventListener('pause', handlePause);
      video.removeEventListener('seeked', handleSeeked);
      // Terminal event so the renderer always has a closing boundary
      addRecordEvent(ClipSyncEventType.PAUSE, video.currentTime);
    };
  }, [isRecording, videoRef, addRecordEvent]);
}
