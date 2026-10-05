import { describe, expect, it } from 'vitest';
import { PlaybackStore, type FrameTick } from './playbackStore';
import { FakeScheduler } from './testing/fakeScheduler';
import { FakeFrameVideo, FakeVideo } from './testing/fakeVideo';

function setup<V extends FakeVideo>(video: V) {
  const scheduler = new FakeScheduler();
  const store = new PlaybackStore(scheduler);
  const ticks: FrameTick[] = [];
  store.subscribeFrame((tick) => ticks.push(tick));
  store.attach(video);
  return { scheduler, store, ticks };
}

// D8: a 2D nézetben a <video> el van rejtve, de a lejátszás megy; a böngésző ilyenkor
// nem feltétlenül hív képkocka-visszahívást, a tick-eknek ettől függetlenül jönniük kell.
describe('PlaybackStore rAF-tartalék', () => {
  it('ha a képkocka-visszahívás hallgat, lejátszás közben a rAF ad tick-et', () => {
    const video = new FakeFrameVideo();
    const { scheduler, store, ticks } = setup(video);
    store.play();
    video.advanceTime(0.5);
    scheduler.frame(200);
    expect(ticks.map((t) => t.mediaTime)).toEqual([0.5]);
  });

  it('ha az idő nem változott, nem ad ismétlődő tick-et', () => {
    const video = new FakeFrameVideo();
    const { scheduler, store, ticks } = setup(video);
    store.play();
    video.advanceTime(0.5);
    scheduler.frame(200);
    scheduler.frame(16);
    expect(ticks).toHaveLength(1);
  });

  it('amíg a képkocka-visszahívás tick-et ad, a rAF-tartalék hallgat', () => {
    const video = new FakeFrameVideo();
    const { scheduler, store, ticks } = setup(video);
    store.play();
    scheduler.time = 1000;
    video.presentFrame(0.1);
    video.advanceTime(0.2);
    scheduler.frame(16);
    expect(ticks.map((t) => t.mediaTime)).toEqual([0.1]);
  });

  it('lassított lejátszásnál hosszabb csendet is megvár a visszahívásra', () => {
    const video = new FakeFrameVideo();
    const { scheduler, store, ticks } = setup(video);
    store.setRate(0.25);
    store.play();
    scheduler.time = 1000;
    video.presentFrame(0.1);
    video.advanceTime(0.2);
    scheduler.frame(200);
    expect(ticks.map((t) => t.mediaTime)).toEqual([0.1]);
    scheduler.frame(300);
    expect(ticks.map((t) => t.mediaTime)).toEqual([0.1, 0.2]);
  });

  it('visszahívást nem ismerő böngészőben mindig a rAF ad tick-et', () => {
    const video = new FakeVideo();
    const { scheduler, store, ticks } = setup(video);
    store.play();
    video.advanceTime(0.3);
    scheduler.frame(16);
    expect(ticks.map((t) => t.mediaTime)).toEqual([0.3]);
  });

  it('szünetelés után a rAF-ciklus magától leáll', () => {
    const video = new FakeFrameVideo();
    const { scheduler, store } = setup(video);
    store.play();
    expect(scheduler.pendingFrames).toBe(1);
    store.pause();
    scheduler.frame(16);
    expect(scheduler.pendingFrames).toBe(0);
  });

  it('újraindított lejátszásnál nem indul kétszeres ciklus', () => {
    const video = new FakeFrameVideo();
    const { scheduler, store } = setup(video);
    store.play();
    store.pause();
    store.play();
    expect(scheduler.pendingFrames).toBe(1);
  });

  it('leválasztáskor a rAF-ciklus törlődik', () => {
    const video = new FakeFrameVideo();
    const { scheduler, store } = setup(video);
    store.play();
    store.attach(null);
    expect(scheduler.pendingFrames).toBe(0);
  });
});
