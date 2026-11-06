import { describe, expect, it } from 'vitest';
import type { BallEntry, TrackingEntry } from '../types/trackingData';
import {
  createTrackingStore,
  EMPTY_TRACKING_STORE,
  FRAME_OFFSET,
  PLAYER_HOLD_FRAMES,
  timeToFrame,
} from './trackingStore';

const player = (frame: number, player_id: number): TrackingEntry => ({
  frame,
  player_id,
  x1: 0,
  y1: 0,
  x2: 10,
  y2: 20,
});

const ball = (frame: number): BallEntry => ({ frame, x1: 0, y1: 0, x2: 1, y2: 1 });

describe('timeToFrame', () => {
  it('képkockára kerekít az fps alapján', () => {
    expect(timeToFrame(0, 50)).toBe(0 + FRAME_OFFSET);
    expect(timeToFrame(1, 50)).toBe(50 + FRAME_OFFSET);
    expect(timeToFrame(0.0199, 50)).toBe(1 + FRAME_OFFSET);
  });

  it('nem ad negatív indexet', () => {
    expect(timeToFrame(-1, 50)).toBe(0);
  });

  it('érvénytelen fps-nél 0-t ad', () => {
    expect(timeToFrame(5, 0)).toBe(0);
    expect(timeToFrame(5, Number.NaN)).toBe(0);
  });
});

describe('createTrackingStore', () => {
  const store = createTrackingStore({
    videoFps: 50,
    trackingData: [player(10, 1), player(10, 2), player(11, 1)],
    ballData: [ball(10)],
  });

  it('pontos képkockán a hozzá tartozó játékosokat adja', () => {
    expect(store.playersAt(10).map((p) => p.player_id)).toEqual([1, 2]);
    expect(store.playersAt(11).map((p) => p.player_id)).toEqual([1]);
  });

  it('hiányzó képkockánál az utolsó ismertet tartja meg', () => {
    expect(store.playersAt(11 + PLAYER_HOLD_FRAMES).map((p) => p.player_id)).toEqual([1]);
  });

  it('a tartási határ után üres', () => {
    expect(store.playersAt(11 + PLAYER_HOLD_FRAMES + 1)).toEqual([]);
  });

  it('nem néz a jövőbe', () => {
    expect(store.playersAt(9)).toEqual([]);
  });

  it('a labdát csak pontos képkockán adja', () => {
    expect(store.ballAt(10)?.frame).toBe(10);
    expect(store.ballAt(11)).toBeUndefined();
  });

  it('ballData nélkül is működik', () => {
    const noBall = createTrackingStore({ videoFps: 25, trackingData: [player(0, 1)] });
    expect(noBall.ballAt(0)).toBeUndefined();
    expect(noBall.fps).toBe(25);
  });
});

describe('createTrackingStore: a 2D koordináták simítása', () => {
  // Egy játékos álló pozíciója körül kockánként ±0,3 egységet rezeg.
  const jittery = (frame: number): TrackingEntry => ({
    ...player(frame, 1),
    tx: 10 + (frame % 2 ? 0.3 : -0.3),
    ty: 20,
  });
  const data = {
    videoFps: 50,
    trackingData: Array.from({ length: 60 }, (_, frame) => jittery(frame)),
    ballData: [{ ...ball(30), tx: 5.5, ty: 6.5 }],
  };

  it('alapértelmezetten simít, a nyers értéket pedig megtartja', () => {
    const entry = createTrackingStore(data).playersAt(30)[0];
    expect(Math.abs((entry.tx as number) - 10)).toBeLessThan(0.05);
    expect(entry.rawTx).toBeCloseTo(10 - 0.3, 9);
    expect(entry.ty).toBeCloseTo(20, 9);
  });

  it('smoothing: false esetén a nyers adatot adja', () => {
    const entry = createTrackingStore(data, { smoothing: false }).playersAt(30)[0];
    expect(entry.tx).toBeCloseTo(10 - 0.3, 9);
    expect(entry.rawTx).toBeUndefined();
  });

  it('a dobozt és a labdát nem simítja', () => {
    const store = createTrackingStore(data);
    const entry = store.playersAt(30)[0];
    expect([entry.x1, entry.y1, entry.x2, entry.y2]).toEqual([0, 0, 10, 20]);
    expect(store.ballAt(30)).toEqual({ ...ball(30), tx: 5.5, ty: 6.5 });
  });

  it('a simított értéket adja a követő rajzok keresése is (ugyanaz az entry, mint a lejátszásé)', () => {
    const store = createTrackingStore(data);
    expect(store.findPlayer(30, 1, 5)?.entry).toBe(store.playersAt(30)[0]);
  });
});

describe('findPlayer', () => {
  const store = createTrackingStore({
    videoFps: 50,
    trackingData: [player(100, 1), player(100, 2), player(110, 1)],
  });

  it('pontos képkockán 0 távolságot ad', () => {
    expect(store.findPlayer(100, 2, 30)).toEqual({ entry: player(100, 2), gap: 0 });
  });

  it('a közelebbi képkockát választja, előre és hátra is keres', () => {
    expect(store.findPlayer(103, 1, 30)?.gap).toBe(3);
    expect(store.findPlayer(103, 1, 30)?.entry.frame).toBe(100);
    expect(store.findPlayer(108, 1, 30)?.entry.frame).toBe(110);
  });

  it('a tűréshatáron túl nem talál', () => {
    expect(store.findPlayer(200, 2, 30)).toBeNull();
    expect(store.findPlayer(100, 99, 30)).toBeNull();
  });
});

describe('EMPTY_TRACKING_STORE', () => {
  it('mindenre üreset ad', () => {
    expect(EMPTY_TRACKING_STORE.playersAt(5)).toEqual([]);
    expect(EMPTY_TRACKING_STORE.ballAt(5)).toBeUndefined();
    expect(EMPTY_TRACKING_STORE.findPlayer(5, 1, 30)).toBeNull();
    expect(EMPTY_TRACKING_STORE.fps).toBe(0);
  });
});
