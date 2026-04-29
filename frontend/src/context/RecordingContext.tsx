import React, { createContext, useContext, useState, useRef, type ReactNode } from 'react';
import type { ClipDrawing } from '../types/drawings';
import type { RecordData } from '../types/recordData';
import { type RecordActionType } from '../constants/recordActionTypes';

interface RecordingContextType {
  isRecording: boolean;
  startRecording: () => void;
  stopRecording: () => void;

  selectedMicId: string | null;
  setSelectedMicId: (id: string | null) => void;

  activeDrawTool: 'none' | 'pen' | 'arrow' | 'circle';
  setActiveDrawTool: (tool: 'none' | 'pen' | 'arrow' | 'circle') => void;
  activeDrawColor: string;
  setActiveDrawColor: (color: string) => void;

  followPlayerMode: boolean;
  setFollowPlayerMode: (active: boolean) => void;
  selectedPlayerId: number | null;
  setSelectedPlayerId: (id: number | null) => void;

  drawings: ClipDrawing[];
  drawingsRef: React.RefObject<ClipDrawing[]>;
  addDrawing: (drawing: ClipDrawing) => void;
  undoLastDrawing: () => void;
  clearDrawings: () => void;
  undoTrigger: number;
  triggerUndo: () => void;
  clearTrigger: number;
  triggerClear: () => void;

  recordDataRef: React.RefObject<RecordData[]>;
  addRecordEvent: (type: RecordActionType, m: number) => void;
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
  const [selectedMicId, setSelectedMicId] = useState<string | null>(null);

  const drawingsRef = useRef<ClipDrawing[]>([]);
  const recordDataRef = useRef<RecordData[]>([]);
  const recordingStartTimeRef = useRef<number | null>(null);

  const startRecording = () => {
    recordingStartTimeRef.current = Date.now();
    recordDataRef.current = [];
    drawingsRef.current = [];
    setDrawings([]);
    setIsRecording(true);
  };

  const stopRecording = () => {
    setIsRecording(false);
    drawingsRef.current = [];
    setDrawings([]);
    // recordDataRef intentionally not cleared — useMediaRecorder reads it in onstop
    setSelectedPlayerId(null);
    setFollowPlayerMode(false);
    setActiveDrawTool('none');
  };

  const addDrawing = (drawing: ClipDrawing) => {
    drawingsRef.current = [...drawingsRef.current, drawing];
    setDrawings((prev) => [...prev, drawing]);
  };

  const undoLastDrawing = () => {
    drawingsRef.current = drawingsRef.current.slice(0, -1);
    setDrawings((prev) => prev.slice(0, -1));
  };

  const clearDrawings = () => {
    drawingsRef.current = [];
    setDrawings([]);
  };

  const triggerUndo = () => setUndoTrigger((prev) => prev + 1);
  const triggerClear = () => setClearTrigger((prev) => prev + 1);

  const addRecordEvent = (type: RecordActionType, m: number) => {
    const start = recordingStartTimeRef.current;
    if (start === null) return;
    const t = (Date.now() - start) / 1000;
    recordDataRef.current = [...recordDataRef.current, { t, type, m }];
  };

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
        drawingsRef,
        addDrawing,
        undoLastDrawing,
        clearDrawings,
        undoTrigger,
        triggerUndo,
        clearTrigger,
        triggerClear,
        recordDataRef,
        addRecordEvent,
        selectedMicId,
        setSelectedMicId,
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
