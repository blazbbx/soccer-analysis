import { useEffect } from 'react';
import { useRecording } from '../../context/RecordingContext';
import { RECORD_ACTION_TYPES } from '../../constants/recordActionTypes';

export function useRecordDataCollector(
  videoRef: React.RefObject<HTMLVideoElement | null>
): void {
  const { isRecording, addRecordEvent } = useRecording();

  useEffect(() => {
    if (!isRecording) return;
    const video = videoRef.current;
    if (!video) return;

    const handlePlay = () => addRecordEvent(RECORD_ACTION_TYPES.PLAY, video.currentTime);
    const handlePause = () => addRecordEvent(RECORD_ACTION_TYPES.PAUSE, video.currentTime);
    // 'seeked' fires once the seek is complete and currentTime is settled
    const handleSeeked = () => addRecordEvent(RECORD_ACTION_TYPES.SEEK, video.currentTime);

    video.addEventListener('play', handlePlay);
    video.addEventListener('ratechange', handlePlay);
    video.addEventListener('pause', handlePause);
    video.addEventListener('seeked', handleSeeked);

    return () => {
      video.removeEventListener('play', handlePlay);
      video.removeEventListener('ratechange', handlePlay);
      video.removeEventListener('pause', handlePause);
      video.removeEventListener('seeked', handleSeeked);
    };
  }, [isRecording, videoRef, addRecordEvent]);
}
