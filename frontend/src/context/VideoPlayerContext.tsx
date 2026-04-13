import React, { createContext, useContext, useState, type ReactNode } from 'react';
import type { LabelItemConfig } from '../constants/labels';


export interface PlacedLabel{
  id: string;
  config: LabelItemConfig;
  time: number;
}

export interface ClipItem{
  id: string;
  name: string;
  startTime: number;
  endTime: number;
  color: string;
  isEditing: boolean;
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
  addLabel: (label: Omit<PlacedLabel, 'id'>) => void;
  initLabels: (labels: PlacedLabel[]) => void;
  
  clips: ClipItem[];
  addClip: (clip: ClipItem) => void;
  updateClipTimes: (id: string, newStartTime: number, newEndTime: number) => void;
  updateClipName: (id: string, newName: string) => void;
  toggleClipEditMode: (id: string, isEditing: boolean) => void;
  deleteClip: (id: string) => void;
  
  activeDrawTool: 'none' | 'pen' | 'arrow' | 'circle';
  setActiveDrawTool: (tool: 'none' | 'pen' | 'arrow' | 'circle') => void;
  activeDrawColor: string;
  setActiveDrawColor: (color: string) => void;
  undoTrigger: number;
  triggerUndo: () => void;
  clearTrigger: number;
  triggerClear: () => void;
}

const VideoPlayerContext = createContext<VideoPlayerContextType | undefined>(undefined);


export const VideoPlayerProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  
  const [currentTime, setCurrentTime] = useState<number>(0);
  const [duration, setDuration] = useState<number>(0);
  const [isPlaying, setIsPlaying] = useState<boolean>(false);
  const [playbackRate, setPlaybackRate] = useState<number>(1);
  const [volume, setVolume] = useState<number>(1);
  
  const [labels, setLabels] = useState<PlacedLabel[]>([]);
  const [labelIdCounter, setLabelIdCounter] = useState<number>(0);
  
  const [clips, setClips] = useState<ClipItem[]>([]);
  
  const [activeDrawTool, setActiveDrawTool] = useState<'none' | 'pen' | 'arrow' | 'circle'>('none');
  const [activeDrawColor, setActiveDrawColor] = useState<string>('#f44336'); 
  const [undoTrigger, setUndoTrigger] = useState(0);
  const [clearTrigger, setClearTrigger] = useState(0);

  const triggerUndo = () => setUndoTrigger(prev => prev + 1);
  const triggerClear = () => setClearTrigger(prev => prev + 1);

  
  const handleSkip = (seconds: number) => {
    let newTime = currentTime + seconds;
    if (newTime < 0) newTime = 0;
    if (duration > 0 && newTime > duration) newTime = duration;
    setCurrentTime(newTime);
  };

  const addLabel = (label: Omit<PlacedLabel, 'id'>) => {
    setLabelIdCounter((prev) => prev + 1);
    const newLabel: PlacedLabel = {
      ...label,
      id: `label-${labelIdCounter}`,
    };
    setLabels((prev) => [...prev, newLabel]);
  };

  const initLabels = (incoming: PlacedLabel[]) => {
    setLabels(incoming);
  };

  const addClip = (clip: ClipItem) => {
    setClips((prev) => [...prev,clip]);
  }

  const updateClipTimes = (id: string, newStartTime: number, newEndTime: number) => {
    setClips((prev) => prev.map(c => c.id === id ? { ...c, startTime: newStartTime, endTime: newEndTime } : c));
  };

  const updateClipName = (id: string, newName: string) => {
    setClips((prev) => prev.map(c => c.id === id ? { ...c, name: newName } : c));
  };

  const toggleClipEditMode = (id: string, isEditing: boolean) => {
    setClips((prev) => prev.map(c => c.id === id ? { ...c, isEditing } : c));
  };

  const deleteClip = (id: string) => {
    setClips((prev) => prev.filter(c => c.id !== id));
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
        labels,
        addLabel,
        initLabels,
        clips,
        addClip,
        updateClipTimes,
        updateClipName,
        toggleClipEditMode,
        deleteClip,
        activeDrawTool,
        setActiveDrawTool,
        activeDrawColor, 
        setActiveDrawColor,
        undoTrigger,
        triggerUndo,
        clearTrigger,
        triggerClear,
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