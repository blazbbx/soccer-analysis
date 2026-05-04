import React, {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import type { LabelItemConfig } from "../constants/labels";

export interface PlacedLabel {
  id: string;
  config: LabelItemConfig;
  time: number;
}

// High-frequency: updates on every video timeupdate (4–30×/sec)
interface VideoPlaybackContextType {
  currentTime: number;
  duration: number;
}

// Stable: changes only on user interaction (play/pause, volume, seek action, labels)
interface VideoPlayerContextType {
  setCurrentTime: (time: number) => void;
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

const VideoPlaybackContext = createContext<VideoPlaybackContextType | undefined>(undefined);
const VideoPlayerContext = createContext<VideoPlayerContextType | undefined>(undefined);

export const VideoPlayerProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [currentTime, setCurrentTime] = useState<number>(0);
  const [duration, setDuration] = useState<number>(0);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [playbackRate, setPlaybackRate] = useState<number>(1);
  const [volume, setVolume] = useState<number>(1);
  const [labels, setLabels] = useState<PlacedLabel[]>([]);
  const labelIdCounterRef = React.useRef(0);

  const handleSkip = useCallback((seconds: number) => {
    setCurrentTime((prev) => {
      const next = prev + seconds;
      if (next < 0) return 0;
      return next;
    });
  }, []);

  const addLabel = useCallback((label: Omit<PlacedLabel, "id">) => {
    const id = `label-${labelIdCounterRef.current++}`;
    setLabels((ls) => [...ls, { ...label, id }]);
  }, []);

  const initLabels = useCallback((incoming: PlacedLabel[]) => setLabels(incoming), []);

  const playbackValue = useMemo(
    () => ({ currentTime, duration }),
    [currentTime, duration]
  );

  const playerValue = useMemo(
    () => ({
      setCurrentTime,
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
      initLabels,
    }),
    [isPlaying, playbackRate, volume, labels, handleSkip, addLabel, initLabels]
  );

  return (
    <VideoPlaybackContext.Provider value={playbackValue}>
      <VideoPlayerContext.Provider value={playerValue}>
        {children}
      </VideoPlayerContext.Provider>
    </VideoPlaybackContext.Provider>
  );
};

export const useVideoPlayback = (): VideoPlaybackContextType => {
  const ctx = useContext(VideoPlaybackContext);
  if (!ctx) throw new Error("useVideoPlayback must be used within VideoPlayerProvider");
  return ctx;
};

export const useVideoPlayer = (): VideoPlayerContextType => {
  const ctx = useContext(VideoPlayerContext);
  if (!ctx) throw new Error("useVideoPlayer must be used within VideoPlayerProvider");
  return ctx;
};
