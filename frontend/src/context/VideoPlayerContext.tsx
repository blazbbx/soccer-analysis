import React, {
  createContext,
  useContext,
  useState,
  type ReactNode,
} from "react";
import type { LabelItemConfig } from "../constants/labels";

export interface PlacedLabel {
  id: string;
  config: LabelItemConfig;
  time: number;
}

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

  labels: PlacedLabel[];
  addLabel: (label: Omit<PlacedLabel, "id">) => void;
  initLabels: (labels: PlacedLabel[]) => void;
}

const VideoPlayerContext = createContext<VideoPlayerContextType | undefined>(
  undefined,
);

export const VideoPlayerProvider: React.FC<{ children: ReactNode }> = ({
  children,
}) => {
  const [currentTime, setCurrentTime] = useState<number>(0);
  const [duration, setDuration] = useState<number>(0);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [playbackRate, setPlaybackRate] = useState<number>(1);
  const [volume, setVolume] = useState<number>(1);
  const [labels, setLabels] = useState<PlacedLabel[]>([]);
  const [labelIdCounter, setLabelIdCounter] = useState<number>(0);

  const handleSkip = (seconds: number) => {
    let newTime = currentTime + seconds;
    if (newTime < 0) newTime = 0;
    if (duration > 0 && newTime > duration) newTime = duration;
    setCurrentTime(newTime);
  };

  const addLabel = (label: Omit<PlacedLabel, "id">) => {
    setLabelIdCounter((prev) => prev + 1);
    setLabels((prev) => [...prev, { ...label, id: `label-${labelIdCounter}` }]);
  };

  const initLabels = (incoming: PlacedLabel[]) => setLabels(incoming);

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
        labels,
        addLabel,
        initLabels
      }}
    >
      {children}
    </VideoPlayerContext.Provider>
  );
};

export const useVideoPlayer = () => {
  const context = useContext(VideoPlayerContext);
  if (!context) {
    throw new Error(
      "A useVideoPlayer hookot csak a VideoPlayerProvider-en belül lehet használni!",
    );
  }
  return context;
};
