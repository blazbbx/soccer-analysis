import type { BallEntry, TrackingEntry } from '../types/trackingData';
import type { FrameClock } from './playbackStore';
import { EMPTY_TRACKING_STORE, timeToFrame, type TrackingStore } from './trackingStore';

export interface FrameSnapshot {
  readonly mediaTime: number;
  readonly frame: number;
  readonly players: readonly TrackingEntry[];
  readonly ball: BallEntry | undefined;
}

export const EMPTY_SNAPSHOT: FrameSnapshot = {
  mediaTime: 0,
  frame: 0,
  players: [],
  ball: undefined,
};

export function buildSnapshot(mediaTime: number, tracking: TrackingStore): FrameSnapshot {
  const frame = timeToFrame(mediaTime, tracking.fps);
  return {
    mediaTime,
    frame,
    players: tracking.playersAt(frame),
    ball: tracking.ballAt(frame),
  };
}

export interface FrameSnapshotSource {
  subscribe(cb: () => void): () => void;
  getSnapshot(): FrameSnapshot;
  getTracking(): TrackingStore;
  setTracking(tracking: TrackingStore): void;
  dispose(): void;
}

// Tick-enként egyetlen közös pillanatkép (D3): minden nézet ugyanazt a képkockát látja.
export function createFrameSnapshotSource(
  clock: FrameClock,
  initialTracking: TrackingStore = EMPTY_TRACKING_STORE,
): FrameSnapshotSource {
  let tracking = initialTracking;
  let snapshot: FrameSnapshot = EMPTY_SNAPSHOT;
  const listeners = new Set<() => void>();

  const publish = (mediaTime: number) => {
    snapshot = buildSnapshot(mediaTime, tracking);
    for (const listener of [...listeners]) listener();
  };

  const unsubscribeClock = clock.subscribeFrame(({ mediaTime }) => publish(mediaTime));

  return {
    subscribe(cb) {
      listeners.add(cb);
      return () => {
        listeners.delete(cb);
      };
    },
    getSnapshot: () => snapshot,
    getTracking: () => tracking,
    setTracking(next) {
      tracking = next;
      publish(snapshot.mediaTime);
    },
    dispose() {
      unsubscribeClock();
      listeners.clear();
    },
  };
}
