import { useEffect, useState } from 'react';

export const useVideoBuffering = (
  videoRef: React.RefObject<HTMLVideoElement | null>
): { isBuffering: boolean } => {
  const [isBuffering, setIsBuffering] = useState(false);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    const show = () => setIsBuffering(true);
    const hide = () => setIsBuffering(false);

    video.addEventListener('waiting', show);
    video.addEventListener('seeking', show);
    video.addEventListener('stalled', show);
    video.addEventListener('playing', hide);
    video.addEventListener('seeked', hide);
    video.addEventListener('canplay', hide);

    return () => {
      video.removeEventListener('waiting', show);
      video.removeEventListener('seeking', show);
      video.removeEventListener('stalled', show);
      video.removeEventListener('playing', hide);
      video.removeEventListener('seeked', hide);
      video.removeEventListener('canplay', hide);
    };
  }, [videoRef]);

  return { isBuffering };
};
