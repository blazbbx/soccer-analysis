import React, { forwardRef, useImperativeHandle, useRef, useEffect } from 'react';
import { useVideoPlayback } from '../../../../context/VideoPlayerContext';
import { useHlsVideo } from '../../hooks/VideoEdit/useHlsVideo';
import { useRecordDataCollector } from '../../hooks/VideoEdit/useRecordDataCollector';

interface HlsVideoProps {
  videoUrl: string;
}

export const HlsVideo = forwardRef<HTMLVideoElement, HlsVideoProps>(({ videoUrl }, forwardedRef) => {
  const localRef = useRef<HTMLVideoElement>(null);
  useImperativeHandle(forwardedRef, () => localRef.current!);

  const { currentTime } = useVideoPlayback();
  const { lastSyncedTimeRef } = useHlsVideo(localRef, videoUrl);

  useRecordDataCollector(localRef);

  useEffect(() => {
    const video = localRef.current;
    if (!video) return;
    if (Math.abs(currentTime - lastSyncedTimeRef.current) < 0.1) return;
    video.currentTime = currentTime;
  }, [currentTime, lastSyncedTimeRef]);

  return (
    <video
      ref={localRef}
      style={{
        position: 'absolute',
        top: 0,
        left: 0,
        width: '100%',
        height: '100%',
        pointerEvents: 'none',
      }}
    />
  );
});
