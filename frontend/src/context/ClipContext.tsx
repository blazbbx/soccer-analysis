import React, { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type { ClipDrawing } from '../types/drawings';
import {
  useGetClips,
  useInitiateUpload,
  useUpdateClip,
  useDeleteClip,
  getGetClipsQueryKey,
} from '../api/generated/clip-controller/clip-controller';
import type { ClipResponse } from '../api/generated/model/clipResponse';

const CLIP_COLORS = ['#00e676', '#2196f3', '#ff9800', '#e91e63', '#9c27b0', '#00bcd4', '#ffeb3b'];

export interface ClipItem {
  id: string;
  serverId?: string;
  name: string;
  startTime: number;
  endTime: number;
  color: string;
  isEditing: boolean;
  drawings: ClipDrawing[];
}

interface ClipContextType {
  clips: ClipItem[];
  addClip: (clip: Omit<ClipItem, 'drawings'>) => void;
  updateClipTimes: (id: string, newStartTime: number, newEndTime: number) => void;
  updateClipName: (id: string, newName: string) => void;
  toggleClipEditMode: (id: string, isEditing: boolean) => void;
  saveClip: (id: string) => Promise<void>;
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

export const ClipProvider: React.FC<{ children: ReactNode; matchId: string }> = ({ children, matchId }) => {
  const [clips, setClips] = useState<ClipItem[]>([]);

  const queryClient = useQueryClient();
  const { data: clipsData } = useGetClips(matchId);
  const { mutateAsync: initiateUploadAsync } = useInitiateUpload();
  const { mutateAsync: updateClipAsync } = useUpdateClip();
  const { mutateAsync: deleteClipAsync } = useDeleteClip();

  useEffect(() => {
    if (!clipsData) return;
    const fetched = clipsData as unknown as ClipResponse[];
    setClips((prev) => {
      const localOnlyClips = prev.filter((c) => !c.serverId);
      const serverClips = fetched.map((r, i) => {
        const existing = prev.find((c) => c.serverId === r.id);
        return {
          id: existing?.id ?? r.id ?? `server-${i}`,
          serverId: r.id,
          name: r.name ?? '',
          startTime: r.startSeconds ?? 0,
          endTime: r.endSeconds ?? 1,
          color: existing?.color ?? CLIP_COLORS[i % CLIP_COLORS.length],
          isEditing: existing?.isEditing ?? false,
          drawings: existing?.drawings ?? [],
        };
      });
      return [...serverClips, ...localOnlyClips];
    });
  }, [clipsData]);

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

  const invalidateClips = () =>
    queryClient.invalidateQueries({ queryKey: getGetClipsQueryKey(matchId) });

  const saveClip = async (id: string) => {
    const clip = clips.find((c) => c.id === id);
    if (!clip) return;

    if (!clip.serverId) {
      const response = await initiateUploadAsync({
        matchId,
        data: {
          originalFilename: `${clip.name || 'clip'}.mp4`,
          name: clip.name,
          startSeconds: clip.startTime,
          endSeconds: clip.endTime,
        },
      });
      const { id: newServerId } = response as unknown as ClipResponse;
      setClips((prev) =>
        prev.map((c) => (c.id === id ? { ...c, serverId: newServerId, isEditing: false } : c))
      );
    } else {
      await updateClipAsync({
        matchId,
        clipId: clip.serverId,
        data: {
          name: clip.name,
          startSeconds: clip.startTime,
          endSeconds: clip.endTime,
        },
      });
      setClips((prev) =>
        prev.map((c) => (c.id === id ? { ...c, isEditing: false } : c))
      );
      await invalidateClips();
    }
  };

  const deleteClip = (id: string) => {
    const clip = clips.find((c) => c.id === id);
    if (clip?.serverId) {
      deleteClipAsync({ matchId, clipId: clip.serverId })
        .then(invalidateClips)
        .catch(console.error);
    } else {
      setClips((prev) => prev.filter((c) => c.id !== id));
    }
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
        clips,
        addClip,
        updateClipTimes,
        updateClipName,
        toggleClipEditMode,
        saveClip,
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
