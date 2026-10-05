import { describe, expect, it, vi } from 'vitest';
import type { TrackingEntry } from '../types/trackingData';
import { EMPTY_SNAPSHOT, createFrameSnapshotSource } from './frameSnapshot';
import { PlaybackStore } from './playbackStore';
import { createTrackingStore } from './trackingStore';
import { FakeFrameVideo } from './testing/fakeVideo';

const player = (frame: number, player_id: number): TrackingEntry => ({
  frame,
  player_id,
  x1: 0,
  y1: 0,
  x2: 10,
  y2: 20,
});

const tracking = createTrackingStore({
  videoFps: 50,
  trackingData: [player(50, 7)],
  ballData: [{ frame: 50, x1: 0, y1: 0, x2: 1, y2: 1 }],
});

function setup() {
  const video = new FakeFrameVideo();
  const playback = new PlaybackStore();
  playback.attach(video);
  const source = createFrameSnapshotSource(playback, tracking);
  return { video, playback, source };
}

describe('createFrameSnapshotSource', () => {
  it('kezdetben üres pillanatképet ad', () => {
    const { source } = setup();
    expect(source.getSnapshot()).toBe(EMPTY_SNAPSHOT);
  });

  it('tick-re a mediaTime-hoz tartozó adatot teszi a pillanatképbe', () => {
    const { video, source } = setup();
    video.presentFrame(1);
    const snapshot = source.getSnapshot();
    expect(snapshot.frame).toBe(50);
    expect(snapshot.players.map((p) => p.player_id)).toEqual([7]);
    expect(snapshot.ball?.frame).toBe(50);
  });

  it('két tick között ugyanazt az objektumot adja vissza', () => {
    const { video, source } = setup();
    video.presentFrame(1);
    expect(source.getSnapshot()).toBe(source.getSnapshot());
  });

  it('minden feliratkozót értesít, és a leiratkozás működik', () => {
    const { video, source } = setup();
    const a = vi.fn();
    const b = vi.fn();
    source.subscribe(a);
    const unsubscribeB = source.subscribe(b);
    video.presentFrame(1);
    unsubscribeB();
    video.presentFrame(1.02);
    expect(a).toHaveBeenCalledTimes(2);
    expect(b).toHaveBeenCalledTimes(1);
  });

  it('a tracking-adat későbbi betöltésekor az utolsó idővel újra kiad', () => {
    const video = new FakeFrameVideo();
    const playback = new PlaybackStore();
    playback.attach(video);
    const source = createFrameSnapshotSource(playback);
    video.presentFrame(1);
    expect(source.getSnapshot().players).toEqual([]);

    const listener = vi.fn();
    source.subscribe(listener);
    source.setTracking(tracking);
    expect(listener).toHaveBeenCalledTimes(1);
    expect(source.getSnapshot().players.map((p) => p.player_id)).toEqual([7]);
  });

  it('dispose után nem reagál tick-re', () => {
    const { video, source } = setup();
    const listener = vi.fn();
    source.subscribe(listener);
    source.dispose();
    video.presentFrame(1);
    expect(listener).not.toHaveBeenCalled();
  });
});
