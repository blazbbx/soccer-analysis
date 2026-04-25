import React, { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import type { LabelItemConfig } from '../constants/labels';
import type { ClipDrawing } from '../types/drawings';

export interface PlacedLabel {
  id: string;
  config: LabelItemConfig;
  time: number;
}

export interface ClipItem {
  id: string;
  name: string;
  startTime: number;
  endTime: number;
  color: string;
  isEditing: boolean;
  drawings: ClipDrawing[];
}

interface ClipContextType {
  labels: PlacedLabel[];
  addLabel: (label: Omit<PlacedLabel, 'id'>) => void;
  initLabels: (labels: PlacedLabel[]) => void;

  clips: ClipItem[];
  addClip: (clip: Omit<ClipItem, 'drawings'>) => void;
  updateClipTimes: (id: string, newStartTime: number, newEndTime: number) => void;
  updateClipName: (id: string, newName: string) => void;
  toggleClipEditMode: (id: string, isEditing: boolean) => void;
  deleteClip: (id: string) => void;

  drawingClipId: string | null;
  addDrawingToClip: (clipId: string, drawing: ClipDrawing) => void;
  undoLastDrawingFromClip: (clipId: string) => void;
  clearDrawingsFromClip: (clipId: string) => void;

  activeDrawTool: 'none' | 'pen' | 'arrow' | 'circle';
  setActiveDrawTool: (tool: 'none' | 'pen' | 'arrow' | 'circle') => void;
  activeDrawColor: string;
  setActiveDrawColor: (color: string) => void;

  undoTrigger: number;
  triggerUndo: () => void;
  clearTrigger: number;
  triggerClear: () => void;
  shakeUnsavedTrigger: number;
  triggerShakeUnsaved: () => void;

  followPlayerMode: boolean;
  setFollowPlayerMode: (active: boolean) => void;
  selectedPlayerId: number | null;
  setSelectedPlayerId: (id: number | null) => void;

  clipBounds: { start: number; end: number } | null;
}

const ClipContext = createContext<ClipContextType | undefined>(undefined);

export const ClipProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [labels, setLabels] = useState<PlacedLabel[]>([]);
  const [labelIdCounter, setLabelIdCounter] = useState<number>(0);

  const [clips, setClips] = useState<ClipItem[]>([]);

  const [activeDrawTool, setActiveDrawTool] = useState<'none' | 'pen' | 'arrow' | 'circle'>('none');
  const [activeDrawColor, setActiveDrawColor] = useState<string>('#f44336');
  const [undoTrigger, setUndoTrigger] = useState(0);
  const [clearTrigger, setClearTrigger] = useState(0);
  const [shakeUnsavedTrigger, setShakeUnsavedTrigger] = useState(0);

  const [followPlayerMode, setFollowPlayerMode] = useState(false);
  const [selectedPlayerId, setSelectedPlayerId] = useState<number | null>(null);

  const triggerUndo = () => setUndoTrigger((prev) => prev + 1);
  const triggerClear = () => setClearTrigger((prev) => prev + 1);
  const triggerShakeUnsaved = () => setShakeUnsavedTrigger((prev) => prev + 1);

  const clipBounds = useMemo(() => {
    const editing = clips.find((c) => c.isEditing);
    return editing ? { start: editing.startTime, end: editing.endTime } : null;
  }, [clips]);

  const drawingClipId = useMemo(() => clips.find((c) => c.isEditing)?.id ?? null, [clips]);

  const addLabel = (label: Omit<PlacedLabel, 'id'>) => {
    setLabelIdCounter((prev) => prev + 1);
    setLabels((prev) => [...prev, { ...label, id: `label-${labelIdCounter}` }]);
  };

  const initLabels = (incoming: PlacedLabel[]) => setLabels(incoming);

  const addClip = (clip: Omit<ClipItem, 'drawings'>) => {
    setClips((prev) => [...prev, { ...clip, drawings: [] }]);
  };

  const updateClipTimes = (id: string, newStartTime: number, newEndTime: number) => {
    setClips((prev) =>
      prev.map((c) => (c.id === id ? { ...c, startTime: newStartTime, endTime: newEndTime } : c))
    );
  };

  const updateClipName = (id: string, newName: string) => {
    setClips((prev) => prev.map((c) => (c.id === id ? { ...c, name: newName } : c)));
  };

  const toggleClipEditMode = (id: string, isEditing: boolean) => {
    setClips((prev) => prev.map((c) => (c.id === id ? { ...c, isEditing } : c)));
    if (!isEditing) {
      setSelectedPlayerId(null);
      setFollowPlayerMode(false);
    }
  };

  const deleteClip = (id: string) => {
    setClips((prev) => prev.filter((c) => c.id !== id));
  };

  const addDrawingToClip = (clipId: string, drawing: ClipDrawing) => {
    setClips((prev) =>
      prev.map((c) => (c.id === clipId ? { ...c, drawings: [...c.drawings, drawing] } : c))
    );
  };

  const undoLastDrawingFromClip = (clipId: string) => {
    setClips((prev) =>
      prev.map((c) => (c.id === clipId ? { ...c, drawings: c.drawings.slice(0, -1) } : c))
    );
  };

  const clearDrawingsFromClip = (clipId: string) => {
    setClips((prev) => prev.map((c) => (c.id === clipId ? { ...c, drawings: [] } : c)));
  };

  return (
    <ClipContext.Provider
      value={{
        labels,
        addLabel,
        initLabels,
        clips,
        addClip,
        updateClipTimes,
        updateClipName,
        toggleClipEditMode,
        deleteClip,
        drawingClipId,
        addDrawingToClip,
        undoLastDrawingFromClip,
        clearDrawingsFromClip,
        activeDrawTool,
        setActiveDrawTool,
        activeDrawColor,
        setActiveDrawColor,
        undoTrigger,
        triggerUndo,
        clearTrigger,
        triggerClear,
        shakeUnsavedTrigger,
        triggerShakeUnsaved,
        followPlayerMode,
        setFollowPlayerMode,
        selectedPlayerId,
        setSelectedPlayerId,
        clipBounds,
      }}
    >
      {children}
    </ClipContext.Provider>
  );
};

export const useClip = () => {
  const context = useContext(ClipContext);
  if (!context) {
    throw new Error('A useClip hookot csak a ClipProvider-en belül lehet használni!');
  }
  return context;
};
