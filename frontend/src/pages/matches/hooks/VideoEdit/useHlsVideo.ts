import Hls from "hls.js";
import { useEffect } from "react";
import { usePlaybackStore } from "../../../../player/hooks";

export const useHlsVideo = (videoRef: React.RefObject<HTMLVideoElement | null>, videoUrl: string) => {
  const playback = usePlaybackStore();

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    playback.attach(video);
    return () => playback.attach(null);
  }, [playback, videoRef]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video || !videoUrl) return;
    playback.reportError(null);
    let hls: Hls | undefined;

    if (Hls.isSupported()) {
      hls = new Hls();
      hls.on(Hls.Events.ERROR, (_event, data) => {
        if (data.fatal) playback.reportError(new Error(`HLS ${data.type}: ${data.details}`));
      });
      hls.loadSource(videoUrl);
      hls.attachMedia(video);
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = videoUrl;
    }
    return () => hls?.destroy();
  }, [videoUrl, videoRef, playback]);
};
