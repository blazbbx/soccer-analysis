import Hls from "hls.js";
import { useEffect } from "react";
import { useVideoPlayer } from "../../context/VideoPlayerContext";


export const useHlsVideo = (videoRef: React.RefObject<HTMLVideoElement | null>, videoUrl: string) => {
  const { volume, playbackRate, isPlaying, setIsPlaying, setDuration, setCurrentTime } = useVideoPlayer();

  
  useEffect(() => {
    const video = videoRef.current;
    if (!video || !videoUrl) return;
    let hls: Hls;

    if (Hls.isSupported()) {
      hls = new Hls();
      hls.loadSource(videoUrl);
      hls.attachMedia(video);
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = videoUrl;
    }
    return () => hls?.destroy();
  }, [videoUrl, videoRef]);

  
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;
    video.volume = volume;
    video.playbackRate = playbackRate;
    
    if (isPlaying) video.play().catch(() => {});
    else video.pause();
  }, [isPlaying, volume, playbackRate, videoRef]);

  
  useEffect(() => {
    const video = videoRef.current;
    if (!video) return;

    const handleTimeUpdate = () => !video.seeking && setCurrentTime(video.currentTime);
    const handleLoaded = () => setDuration(video.duration);
    const handlePlay = () => setIsPlaying(true);
    const handlePause = () => setIsPlaying(false);

    video.addEventListener('timeupdate', handleTimeUpdate);
    video.addEventListener('loadedmetadata', handleLoaded);
    video.addEventListener('play', handlePlay);
    video.addEventListener('pause', handlePause);

    return () => {
      video.removeEventListener('timeupdate', handleTimeUpdate);
      video.removeEventListener('loadedmetadata', handleLoaded);
      video.removeEventListener('play', handlePlay);
      video.removeEventListener('pause', handlePause);
    };
  }, [videoRef, setCurrentTime, setDuration, setIsPlaying]);
};