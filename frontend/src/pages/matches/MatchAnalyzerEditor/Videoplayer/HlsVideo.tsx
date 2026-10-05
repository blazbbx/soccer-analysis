import { forwardRef, useImperativeHandle, useRef } from 'react';
import { useHlsVideo } from '../../hooks/VideoEdit/useHlsVideo';
import { useRecordDataCollector } from '../../hooks/VideoEdit/useRecordDataCollector';

interface HlsVideoProps {
  videoUrl: string;
}

export const HlsVideo = forwardRef<HTMLVideoElement, HlsVideoProps>(({ videoUrl }, forwardedRef) => {
  const localRef = useRef<HTMLVideoElement>(null);
  useImperativeHandle(forwardedRef, () => localRef.current!);

  useHlsVideo(localRef, videoUrl);
  useRecordDataCollector(localRef);

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
