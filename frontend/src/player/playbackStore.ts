export interface FrameTick {
  readonly mediaTime: number;
}

export interface FrameClock {
  subscribeFrame(cb: (tick: FrameTick) => void): () => void;
}

export interface PlaybackState {
  readonly isPlaying: boolean;
  readonly rate: number;
  readonly volume: number;
  readonly duration: number;
  readonly isBuffering: boolean;
  readonly error: Error | null;
}

export interface PlaybackCommands {
  play(): void;
  pause(): void;
  seek(t: number): void;
  skip(dt: number): void;
  setRate(rate: number): void;
  setVolume(volume: number): void;
}

export interface VideoLike extends EventTarget {
  currentTime: number;
  readonly duration: number;
  readonly paused: boolean;
  playbackRate: number;
  volume: number;
  play(): Promise<void>;
  pause(): void;
  requestVideoFrameCallback?(
    callback: (now: number, metadata: { mediaTime: number }) => void,
  ): number;
  cancelVideoFrameCallback?(handle: number): void;
}

export interface PlaybackScheduler {
  requestAnimationFrame?(callback: () => void): number;
  cancelAnimationFrame?(handle: number): void;
  now(): number;
}

function browserScheduler(): PlaybackScheduler {
  return {
    requestAnimationFrame:
      typeof requestAnimationFrame === 'function'
        ? (callback) => requestAnimationFrame(() => callback())
        : undefined,
    cancelAnimationFrame:
      typeof cancelAnimationFrame === 'function' ? (handle) => cancelAnimationFrame(handle) : undefined,
    now: () => performance.now(),
  };
}

// Ha lejátszás közben ennyi ideig nem jön képkocka-visszahívás, a böngésző valószínűleg nem jeleníti
// meg a videót (például a 2D nézetben el van rejtve, D8), ezért a tick-eket a rAF-tartalék adja.
const FRAME_CALLBACK_SILENCE_MS = 100;

const INITIAL_STATE: PlaybackState = {
  isPlaying: false,
  rate: 1,
  volume: 1,
  duration: 0,
  isBuffering: false,
  error: null,
};

const STATE_SYNC_EVENTS = [
  'play',
  'pause',
  'ratechange',
  'volumechange',
  'durationchange',
  'loadedmetadata',
] as const;

const BUFFERING_ON_EVENTS = ['waiting', 'seeking', 'stalled'] as const;
const BUFFERING_OFF_EVENTS = ['playing', 'seeked', 'canplay'] as const;

// A <video> elem az egyetlen igazságforrás (D1): a parancsok közvetlenül az elemre mennek, az állapot
// és az idő pedig az elem eseményeiből jön vissza (D2).
export class PlaybackStore implements PlaybackCommands, FrameClock {
  private video: VideoLike | null = null;
  private state: PlaybackState = INITIAL_STATE;
  private lastTickTime = 0;
  private detachListeners: (() => void) | null = null;
  private readonly stateListeners = new Set<() => void>();
  private readonly frameListeners = new Set<(tick: FrameTick) => void>();
  private readonly scheduler: PlaybackScheduler;

  constructor(scheduler: PlaybackScheduler = browserScheduler()) {
    this.scheduler = scheduler;
  }

  getState = (): PlaybackState => this.state;

  subscribeState = (cb: () => void): (() => void) => {
    this.stateListeners.add(cb);
    return () => {
      this.stateListeners.delete(cb);
    };
  };

  subscribeFrame = (cb: (tick: FrameTick) => void): (() => void) => {
    this.frameListeners.add(cb);
    return () => {
      this.frameListeners.delete(cb);
    };
  };

  getMediaTime(): number {
    return this.video?.currentTime ?? 0;
  }

  getLastTickTime = (): number => this.lastTickTime;

  attach(video: VideoLike | null): void {
    this.detachListeners?.();
    this.detachListeners = null;
    this.video = video;
    this.lastTickTime = video?.currentTime ?? 0;

    if (!video) {
      this.setState(INITIAL_STATE);
      return;
    }

    this.detachListeners = this.listen(video);
    this.syncFromVideo(video);
  }

  play(): void {
    this.video?.play().catch(() => {});
  }

  pause(): void {
    this.video?.pause();
  }

  seek(t: number): void {
    const video = this.video;
    if (!video) return;
    const max = Number.isFinite(video.duration) && video.duration > 0 ? video.duration : Infinity;
    video.currentTime = Math.min(Math.max(0, t), max);
  }

  skip(dt: number): void {
    if (this.video) this.seek(this.video.currentTime + dt);
  }

  setRate(rate: number): void {
    if (this.video) this.video.playbackRate = rate;
  }

  setVolume(volume: number): void {
    if (this.video) this.video.volume = Math.min(1, Math.max(0, volume));
  }

  reportError(error: Error | null): void {
    this.setState({ ...this.state, error });
  }

  private listen(video: VideoLike): () => void {
    const cleanups: (() => void)[] = [];

    const on = (type: string, handler: () => void) => {
      video.addEventListener(type, handler);
      cleanups.push(() => video.removeEventListener(type, handler));
    };

    const sync = () => this.syncFromVideo(video);
    for (const type of STATE_SYNC_EVENTS) on(type, sync);

    for (const type of BUFFERING_ON_EVENTS) on(type, () => this.setBuffering(true));
    for (const type of BUFFERING_OFF_EVENTS) on(type, () => this.setBuffering(false));

    // Szünetelő seek vagy első kép után a képkocka-visszahívás nem feltétlenül fut, ezért ezek
    // az események is tick-et adnak.
    on('seeked', () => this.emitFrame(video.currentTime));
    on('loadeddata', () => this.emitFrame(video.currentTime));

    const hasFrameCallback = typeof video.requestVideoFrameCallback === 'function';
    let lastFrameCallbackAt = -Infinity;

    if (hasFrameCallback) {
      let active = true;
      let handle = 0;
      const request = () => {
        handle = video.requestVideoFrameCallback!((_now, metadata) => {
          if (!active) return;
          lastFrameCallbackAt = this.scheduler.now();
          this.emitFrame(metadata.mediaTime);
          request();
        });
      };
      request();
      cleanups.push(() => {
        active = false;
        video.cancelVideoFrameCallback?.(handle);
      });
    }

    const scheduler = this.scheduler;
    const requestFrame = scheduler.requestAnimationFrame?.bind(scheduler);
    const cancelFrame = scheduler.cancelAnimationFrame?.bind(scheduler);

    if (requestFrame) {
      let frameHandle: number | null = null;
      const fallbackFrame = () => {
        frameHandle = null;
        if (video.paused) return;
        const silenceMs = FRAME_CALLBACK_SILENCE_MS / Math.max(video.playbackRate, 0.1);
        const silent = !hasFrameCallback || scheduler.now() - lastFrameCallbackAt > silenceMs;
        if (silent && video.currentTime !== this.lastTickTime) this.emitFrame(video.currentTime);
        frameHandle = requestFrame(fallbackFrame);
      };
      const start = () => {
        if (frameHandle === null && !video.paused) frameHandle = requestFrame(fallbackFrame);
      };
      on('play', start);
      on('playing', start);
      start();
      cleanups.push(() => {
        if (frameHandle !== null) cancelFrame?.(frameHandle);
        frameHandle = null;
      });
    } else if (!hasFrameCallback) {
      on('timeupdate', () => this.emitFrame(video.currentTime));
    }

    return () => cleanups.forEach((cleanup) => cleanup());
  }

  private syncFromVideo(video: VideoLike): void {
    this.setState({
      ...this.state,
      isPlaying: !video.paused,
      rate: video.playbackRate,
      volume: video.volume,
      duration: Number.isFinite(video.duration) ? video.duration : 0,
    });
  }

  private setBuffering(isBuffering: boolean): void {
    this.setState({ ...this.state, isBuffering });
  }

  private setState(next: PlaybackState): void {
    const prev = this.state;
    const unchanged =
      prev.isPlaying === next.isPlaying &&
      prev.rate === next.rate &&
      prev.volume === next.volume &&
      prev.duration === next.duration &&
      prev.isBuffering === next.isBuffering &&
      prev.error === next.error;
    if (unchanged) return;

    this.state = next;
    for (const listener of [...this.stateListeners]) listener();
  }

  private emitFrame(mediaTime: number): void {
    this.lastTickTime = mediaTime;
    const tick: FrameTick = { mediaTime };
    for (const listener of [...this.frameListeners]) listener(tick);
  }
}
