import React, { createContext, useContext, useState, useRef, type ReactNode } from 'react';
import type { ClipSyncEvent } from '../api/generated/model';
import { ClipSyncEventType } from '../api/generated/model';

export interface PendingRecording {
  overlayBlob: Blob;
  audioBlob: Blob | null;
  syncData: ClipSyncEvent[];
}

// Csak a felvétel állapota. A rajzeszközök és a rajzok az Annotation rétegé (annotation/), azoktól
// a felvétel független (D6).
interface RecordingContextType {
  isRecording: boolean;
  startRecording: () => void;
  stopRecording: () => void;

  selectedMicId: string | null;
  setSelectedMicId: (id: string | null) => void;

  recordDataRef: React.RefObject<ClipSyncEvent[]>;
  addRecordEvent: (type: ClipSyncEventType, m: number) => void;

  pendingRecording: PendingRecording | null;
  setPendingRecording: (recording: PendingRecording | null) => void;
}

const RecordingContext = createContext<RecordingContextType | undefined>(undefined);

export const RecordingProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [isRecording, setIsRecording] = useState(false);
  const [selectedMicId, setSelectedMicId] = useState<string | null>(null);

  const recordDataRef = useRef<ClipSyncEvent[]>([]);
  const recordingStartTimeRef = useRef<number | null>(null);
  const [pendingRecording, setPendingRecording] = useState<PendingRecording | null>(null);

  const startRecording = () => {
    recordingStartTimeRef.current = Date.now();
    recordDataRef.current = [];
    setIsRecording(true);
  };

  // A recordDataRef szándékosan nem törlődik: a useMediaRecorder az onstop-ban olvassa.
  const stopRecording = () => {
    setIsRecording(false);
  };

  const addRecordEvent = (type: ClipSyncEventType, m: number) => {
    const start = recordingStartTimeRef.current;
    if (start === null) return;
    const now = (Date.now() - start) / 1000;
    const lastT = recordDataRef.current.at(-1)?.t ?? -Infinity;
    const t = now > lastT ? now : lastT + 0.001;
    recordDataRef.current = [...recordDataRef.current, { t, type, m }];
  };

  return (
    <RecordingContext.Provider
      value={{
        isRecording,
        startRecording,
        stopRecording,
        recordDataRef,
        addRecordEvent,
        selectedMicId,
        setSelectedMicId,
        pendingRecording,
        setPendingRecording,
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
