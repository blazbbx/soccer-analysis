import { createContext } from 'react';
import type { FrameSnapshotSource } from './frameSnapshot';
import type { PlaybackStore } from './playbackStore';

export interface PlayerContextValue {
  playback: PlaybackStore;
  snapshots: FrameSnapshotSource;
}

export const PlayerContext = createContext<PlayerContextValue | null>(null);
