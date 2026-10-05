import { useContext, useSyncExternalStore } from 'react';
import type { FrameSnapshot, FrameSnapshotSource } from './frameSnapshot';
import type { PlaybackCommands, PlaybackState, PlaybackStore } from './playbackStore';
import { PlayerContext, type PlayerContextValue } from './playerContext';
import { quantizeTime } from './quantizeTime';

function usePlayerContext(): PlayerContextValue {
  const ctx = useContext(PlayerContext);
  if (!ctx) throw new Error('A player hookok a PlayerProvideren belül használhatók');
  return ctx;
}

export const usePlaybackStore = (): PlaybackStore => usePlayerContext().playback;

// A parancsokat metódusként kell hívni (playback.play()), destrukturálva elveszik a `this`.
export const usePlaybackCommands = (): PlaybackCommands => usePlayerContext().playback;

export const useFrameSnapshotSource = (): FrameSnapshotSource => usePlayerContext().snapshots;

export function usePlaybackState(): PlaybackState {
  const { playback } = usePlayerContext();
  return useSyncExternalStore(playback.subscribeState, playback.getState);
}

// A lejátszási idő. resolution = 0: minden megjelenített képkockánál újrarenderel; ha nagyobb
// (másodperc), csak akkor, ha az érték átlép egy lépcsőt.
export function useMediaTime(resolution = 0): number {
  const { playback } = usePlayerContext();
  return useSyncExternalStore(playback.subscribeFrame, () =>
    quantizeTime(playback.getLastTickTime(), resolution),
  );
}

export function useFrameSnapshot(): FrameSnapshot {
  const { snapshots } = usePlayerContext();
  return useSyncExternalStore(snapshots.subscribe, snapshots.getSnapshot);
}
