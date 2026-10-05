import { describe, expect, it, vi } from 'vitest';
import { PlaybackStore, type FrameTick } from './playbackStore';
import { FakeFrameVideo, FakeVideo } from './testing/fakeVideo';

function collectTicks(store: PlaybackStore): FrameTick[] {
  const ticks: FrameTick[] = [];
  store.subscribeFrame((tick) => ticks.push(tick));
  return ticks;
}

describe('PlaybackStore állapot', () => {
  it('csatolás nélkül alapértelmezett állapotot ad, a parancsok nem hibáznak', () => {
    const store = new PlaybackStore();
    expect(store.getState().isPlaying).toBe(false);
    expect(() => {
      store.play();
      store.pause();
      store.seek(5);
      store.skip(5);
      store.setRate(2);
      store.setVolume(0.5);
    }).not.toThrow();
  });

  it('csatoláskor beolvassa az elem állapotát', () => {
    const video = new FakeVideo();
    video.duration = 3600;
    const store = new PlaybackStore();
    store.attach(video);
    expect(store.getState().duration).toBe(3600);
    expect(store.getState().rate).toBe(1);
  });

  it('a play/pause parancs az elem eseményein át frissíti az állapotot', () => {
    const video = new FakeVideo();
    const store = new PlaybackStore();
    store.attach(video);
    store.play();
    expect(store.getState().isPlaying).toBe(true);
    store.pause();
    expect(store.getState().isPlaying).toBe(false);
  });

  it('a sebesség és a hangerő parancs az elemet állítja, az állapot onnan jön vissza', () => {
    const video = new FakeVideo();
    const store = new PlaybackStore();
    store.attach(video);
    store.setRate(2);
    store.setVolume(0.25);
    expect(video.playbackRate).toBe(2);
    expect(store.getState().rate).toBe(2);
    expect(store.getState().volume).toBe(0.25);
  });

  it('a hangerőt 0 és 1 közé szorítja', () => {
    const video = new FakeVideo();
    const store = new PlaybackStore();
    store.attach(video);
    store.setVolume(5);
    expect(video.volume).toBe(1);
    store.setVolume(-1);
    expect(video.volume).toBe(0);
  });

  it('csak tényleges változásnál értesíti az állapot-feliratkozókat', () => {
    const video = new FakeVideo();
    const store = new PlaybackStore();
    store.attach(video);
    const listener = vi.fn();
    store.subscribeState(listener);
    store.setRate(2);
    store.setRate(2);
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('a pufferelést a waiting/seeking kapcsolja be, a playing/seeked/canplay ki', () => {
    const video = new FakeVideo();
    const store = new PlaybackStore();
    store.attach(video);
    video.dispatchEvent(new Event('waiting'));
    expect(store.getState().isBuffering).toBe(true);
    video.dispatchEvent(new Event('playing'));
    expect(store.getState().isBuffering).toBe(false);
  });

  it('a hibát jelenteni lehet és törölni', () => {
    const store = new PlaybackStore();
    const error = new Error('hls');
    store.reportError(error);
    expect(store.getState().error).toBe(error);
    store.reportError(null);
    expect(store.getState().error).toBeNull();
  });

  it('leválasztáskor visszaáll az alapállapotra, és az elem eseményeit már nem figyeli', () => {
    const video = new FakeVideo();
    const store = new PlaybackStore();
    store.attach(video);
    store.play();
    store.attach(null);
    expect(store.getState().isPlaying).toBe(false);
    video.pause();
    video.dispatchEvent(new Event('play'));
    expect(store.getState().isPlaying).toBe(false);
  });
});

describe('PlaybackStore parancsok', () => {
  it('a seek az időtartamra szorít', () => {
    const video = new FakeVideo();
    video.duration = 60;
    const store = new PlaybackStore();
    store.attach(video);
    store.seek(500);
    expect(video.currentTime).toBe(60);
    store.seek(-5);
    expect(video.currentTime).toBe(0);
  });

  it('a skip az elem valódi idejéből számol, nem egy tárolt értékből', () => {
    const video = new FakeVideo();
    const store = new PlaybackStore();
    store.attach(video);
    video.currentTime = 100;
    store.skip(-5);
    expect(video.currentTime).toBe(95);
  });
});

describe('PlaybackStore tick-ek', () => {
  it('requestVideoFrameCallback esetén minden megjelenített képkocka tick-et ad', () => {
    const video = new FakeFrameVideo();
    const store = new PlaybackStore();
    store.attach(video);
    const ticks = collectTicks(store);
    video.presentFrame(0.02);
    video.presentFrame(0.04);
    expect(ticks.map((t) => t.mediaTime)).toEqual([0.02, 0.04]);
  });

  it('a visszahívást minden képkocka után újra regisztrálja', () => {
    const video = new FakeFrameVideo();
    const store = new PlaybackStore();
    store.attach(video);
    video.presentFrame(0.02);
    expect(video.pendingFrameCallbacks).toBe(1);
  });

  it('szünetelő seek után a seeked esemény is tick-et ad', () => {
    const video = new FakeFrameVideo();
    const store = new PlaybackStore();
    store.attach(video);
    const ticks = collectTicks(store);
    store.seek(12.5);
    expect(ticks.map((t) => t.mediaTime)).toEqual([12.5]);
  });

  it('a loadeddata esemény az első képkockához tick-et ad', () => {
    const video = new FakeFrameVideo();
    const store = new PlaybackStore();
    store.attach(video);
    const ticks = collectTicks(store);
    video.dispatchEvent(new Event('loadeddata'));
    expect(ticks).toHaveLength(1);
  });

  it('requestVideoFrameCallback nélkül a timeupdate ad tick-et', () => {
    const video = new FakeVideo();
    const store = new PlaybackStore();
    store.attach(video);
    const ticks = collectTicks(store);
    video.dispatchEvent(new Event('timeupdate'));
    expect(ticks).toHaveLength(1);
  });

  it('leválasztás után nem jön tick, és a függő visszahívás törlődik', () => {
    const video = new FakeFrameVideo();
    const store = new PlaybackStore();
    store.attach(video);
    const ticks = collectTicks(store);
    store.attach(null);
    video.presentFrame(1);
    video.dispatchEvent(new Event('seeked'));
    expect(ticks).toHaveLength(0);
    expect(video.pendingFrameCallbacks).toBe(0);
  });

  it('az utolsó tick idejét tárolja, csatoláskor az elem idejéről indul, leválasztáskor nullázódik', () => {
    const video = new FakeFrameVideo();
    video.currentTime = 7;
    const store = new PlaybackStore();
    store.attach(video);
    expect(store.getLastTickTime()).toBe(7);
    video.presentFrame(9.5);
    expect(store.getLastTickTime()).toBe(9.5);
    store.attach(null);
    expect(store.getLastTickTime()).toBe(0);
  });

  it('a leiratkozott feliratkozó nem kap tick-et', () => {
    const video = new FakeFrameVideo();
    const store = new PlaybackStore();
    store.attach(video);
    const received: number[] = [];
    const unsubscribe = store.subscribeFrame((tick) => received.push(tick.mediaTime));
    video.presentFrame(1);
    unsubscribe();
    video.presentFrame(2);
    expect(received).toEqual([1]);
  });
});
