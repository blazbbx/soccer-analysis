import type { BallEntry, TrackingDataResponse, TrackingEntry } from '../types/trackingData';
import { DEFAULT_SMOOTHING, smoothPlayerPositions, type SmoothingOptions } from './smoothing';

// A backend 0-alapú képkocka-indexet ad (video_processor.py: frame_idx). Az esetleges eltolást egyetlen
// helyen kalibráljuk; a mérés még hátravan.
export const FRAME_OFFSET = 0;

// Ha egy képkockán nincs detekció, az utolsó ismert játékos-állapotot tartjuk meg ennyi képkockáig.
export const PLAYER_HOLD_FRAMES = 5;

export function timeToFrame(mediaTime: number, fps: number): number {
  if (!(fps > 0)) return 0;
  return Math.max(0, Math.round(mediaTime * fps) + FRAME_OFFSET);
}

export interface PlayerLookup {
  readonly entry: TrackingEntry;
  // Hány képkockányira van a keresett képkockától (0 = pontos találat).
  readonly gap: number;
}

export interface TrackingStore {
  readonly fps: number;
  playersAt(frame: number): readonly TrackingEntry[];
  ballAt(frame: number): BallEntry | undefined;
  findPlayer(frame: number, playerId: number, tolerance: number): PlayerLookup | null;
}

type TrackingSource = Pick<TrackingDataResponse, 'videoFps' | 'trackingData' | 'ballData'>;

const NO_PLAYERS: readonly TrackingEntry[] = [];

export const EMPTY_TRACKING_STORE: TrackingStore = {
  fps: 0,
  playersAt: () => NO_PLAYERS,
  ballAt: () => undefined,
  findPlayer: () => null,
};

export interface TrackingStoreOptions {
  // A játékosok `tx`/`ty` (2D) koordinátáinak simítása betöltéskor, egyszer (player/smoothing.ts). `false` = nyers.
  // A dobozt (x1..y2) és a labdát nem érinti.
  readonly smoothing?: SmoothingOptions | false;
}

export function createTrackingStore(data: TrackingSource, options: TrackingStoreOptions = {}): TrackingStore {
  const smoothing = options.smoothing === undefined ? DEFAULT_SMOOTHING : options.smoothing;
  const trackingData = smoothing ? smoothPlayerPositions(data.trackingData, data.videoFps, smoothing) : data.trackingData;

  const players = new Map<number, TrackingEntry[]>();
  for (const entry of trackingData) {
    const bucket = players.get(entry.frame);
    if (bucket) bucket.push(entry);
    else players.set(entry.frame, [entry]);
  }

  const balls = new Map<number, BallEntry>();
  for (const entry of data.ballData ?? []) balls.set(entry.frame, entry);

  const playerIn = (frame: number, playerId: number) =>
    players.get(frame)?.find((entry) => entry.player_id === playerId);

  return {
    fps: data.videoFps,
    playersAt(frame) {
      for (let back = 0; back <= PLAYER_HOLD_FRAMES; back++) {
        const entries = players.get(frame - back);
        if (entries) return entries;
      }
      return NO_PLAYERS;
    },
    ballAt: (frame) => balls.get(frame),
    // A követő rajzok a játékost a kiindulási képkockától távolabb is megtalálják (gap > 0 = halványítva).
    findPlayer(frame, playerId, tolerance) {
      for (let gap = 0; gap <= tolerance; gap++) {
        const ahead = playerIn(frame + gap, playerId);
        if (ahead) return { entry: ahead, gap };
        if (gap > 0) {
          const behind = playerIn(frame - gap, playerId);
          if (behind) return { entry: behind, gap };
        }
      }
      return null;
    },
  };
}
