import React, { createContext, useContext, useState, type ReactNode } from 'react';

interface VideoPlayerContextType {
  currentTime: number;
  setCurrentTime: (time: number) => void;

  duration: number;
  setDuration: (duration: number) => void;

  isPlaying: boolean;
  setIsPlaying: (playing: boolean) => void;

  playbackRate: number;
  setPlaybackRate: (rate: number) => void;

  volume: number;
  setVolume: (volume: number) => void;

  handleSkip: (seconds: number) => void;
}

const VideoPlayerContext = createContext<VideoPlayerContextType | undefined>(undefined);

export const VideoPlayerProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [currentTime, setCurrentTime] = useState<number>(0);
  const [duration, setDuration] = useState<number>(0);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [playbackRate, setPlaybackRate] = useState<number>(1);
  const [volume, setVolume] = useState<number>(1);

  const handleSkip = (seconds: number) => {
    let newTime = currentTime + seconds;
    if (newTime < 0) newTime = 0;
    if (duration > 0 && newTime > duration) newTime = duration;
    setCurrentTime(newTime);
  };

  return (
    <VideoPlayerContext.Provider
      value={{
        currentTime,
        setCurrentTime,
        duration,
        setDuration,
        isPlaying,
        setIsPlaying,
        playbackRate,
        setPlaybackRate,
        volume,
        setVolume,
        handleSkip,
      }}
    >
      {children}
    </VideoPlayerContext.Provider>
  );
};

export const useVideoPlayer = () => {
  const context = useContext(VideoPlayerContext);
  if (!context) {
    throw new Error('A useVideoPlayer hookot csak a VideoPlayerProvider-en belül lehet használni!');
  }
  return context;
};
