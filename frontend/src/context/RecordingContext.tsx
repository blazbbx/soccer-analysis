import React, { createContext, useContext, useState, type ReactNode } from 'react';
import type { ClipDrawing } from '../types/drawings';

interface RecordingContextType {
  isRecording: boolean;
  startRecording: () => void;
  stopRecording: () => void;

  activeDrawTool: 'none' | 'pen' | 'arrow' | 'circle';
  setActiveDrawTool: (tool: 'none' | 'pen' | 'arrow' | 'circle') => void;
  activeDrawColor: string;
  setActiveDrawColor: (color: string) => void;

  followPlayerMode: boolean;
  setFollowPlayerMode: (active: boolean) => void;
  selectedPlayerId: number | null;
  setSelectedPlayerId: (id: number | null) => void;

  drawings: ClipDrawing[];
  addDrawing: (drawing: ClipDrawing) => void;
  undoLastDrawing: () => void;
  clearDrawings: () => void;
  undoTrigger: number;
  triggerUndo: () => void;
  clearTrigger: number;
  triggerClear: () => void;
}

const RecordingContext = createContext<RecordingContextType | undefined>(undefined);

export const RecordingProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [isRecording, setIsRecording] = useState(false);
  const [activeDrawTool, setActiveDrawTool] = useState<'none' | 'pen' | 'arrow' | 'circle'>('none');
  const [activeDrawColor, setActiveDrawColor] = useState('#f44336');
  const [followPlayerMode, setFollowPlayerMode] = useState(false);
  const [selectedPlayerId, setSelectedPlayerId] = useState<number | null>(null);
  const [drawings, setDrawings] = useState<ClipDrawing[]>([]);
  const [undoTrigger, setUndoTrigger] = useState(0);
  const [clearTrigger, setClearTrigger] = useState(0);

  const startRecording = () => {
    setDrawings([]);
    setIsRecording(true);
  };

  const stopRecording = () => {
    setIsRecording(false);
    setDrawings([]);
    setSelectedPlayerId(null);
    setFollowPlayerMode(false);
    setActiveDrawTool('none');
  };

  const addDrawing = (drawing: ClipDrawing) => setDrawings((prev) => [...prev, drawing]);
  const undoLastDrawing = () => setDrawings((prev) => prev.slice(0, -1));
  const clearDrawings = () => setDrawings([]);
  const triggerUndo = () => setUndoTrigger((prev) => prev + 1);
  const triggerClear = () => setClearTrigger((prev) => prev + 1);

  return (
    <RecordingContext.Provider
      value={{
        isRecording,
        startRecording,
        stopRecording,
        activeDrawTool,
        setActiveDrawTool,
        activeDrawColor,
        setActiveDrawColor,
        followPlayerMode,
        setFollowPlayerMode,
        selectedPlayerId,
        setSelectedPlayerId,
        drawings,
        addDrawing,
        undoLastDrawing,
        clearDrawings,
        undoTrigger,
        triggerUndo,
        clearTrigger,
        triggerClear,
      }}
    >
      {children}
    </RecordingContext.Provider>
  );
};

export const useRecording = () => {
  const context = useContext(RecordingContext);
  if (!context) throw new Error('useRecording must be used within RecordingProvider');
  return context;
};
