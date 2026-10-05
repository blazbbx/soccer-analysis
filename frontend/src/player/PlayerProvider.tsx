import { useState, type ReactNode } from 'react';
import { createFrameSnapshotSource } from './frameSnapshot';
import { PlaybackStore } from './playbackStore';
import { PlayerContext, type PlayerContextValue } from './playerContext';

export function PlayerProvider({ children }: { children: ReactNode }) {
  const [value] = useState<PlayerContextValue>(() => {
    const playback = new PlaybackStore();
    return { playback, snapshots: createFrameSnapshotSource(playback) };
  });

  return <PlayerContext.Provider value={value}>{children}</PlayerContext.Provider>;
}
