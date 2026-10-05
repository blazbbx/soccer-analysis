import type { VideoLike } from '../playbackStore';

export class FakeVideo extends EventTarget implements VideoLike {
  duration = 100;
  paused = true;
  private time = 0;
  private rate = 1;
  private vol = 1;

  get currentTime(): number {
    return this.time;
  }

  set currentTime(value: number) {
    this.time = value;
    this.dispatchEvent(new Event('seeking'));
    this.dispatchEvent(new Event('seeked'));
  }

  // Lejátszás közbeni időhaladás: nem vált ki seek eseményt.
  advanceTime(value: number): void {
    this.time = value;
  }

  get playbackRate(): number {
    return this.rate;
  }

  set playbackRate(value: number) {
    this.rate = value;
    this.dispatchEvent(new Event('ratechange'));
  }

  get volume(): number {
    return this.vol;
  }

  set volume(value: number) {
    this.vol = value;
    this.dispatchEvent(new Event('volumechange'));
  }

  play(): Promise<void> {
    this.paused = false;
    this.dispatchEvent(new Event('play'));
    this.dispatchEvent(new Event('playing'));
    return Promise.resolve();
  }

  pause(): void {
    this.paused = true;
    this.dispatchEvent(new Event('pause'));
  }
}

// Olyan böngésző, amely támogatja a requestVideoFrameCallback-et.
export class FakeFrameVideo extends FakeVideo {
  private pending = new Map<number, (now: number, metadata: { mediaTime: number }) => void>();
  private nextHandle = 1;

  requestVideoFrameCallback(callback: (now: number, metadata: { mediaTime: number }) => void): number {
    const handle = this.nextHandle++;
    this.pending.set(handle, callback);
    return handle;
  }

  cancelVideoFrameCallback(handle: number): void {
    this.pending.delete(handle);
  }

  get pendingFrameCallbacks(): number {
    return this.pending.size;
  }

  presentFrame(mediaTime: number): void {
    const callbacks = [...this.pending.values()];
    this.pending.clear();
    for (const callback of callbacks) callback(0, { mediaTime });
  }
}
