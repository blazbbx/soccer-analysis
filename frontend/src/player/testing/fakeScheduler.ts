import type { PlaybackScheduler } from '../playbackStore';

export class FakeScheduler implements PlaybackScheduler {
  time = 0;
  private callbacks = new Map<number, () => void>();
  private nextHandle = 1;

  requestAnimationFrame(callback: () => void): number {
    const handle = this.nextHandle++;
    this.callbacks.set(handle, callback);
    return handle;
  }

  cancelAnimationFrame(handle: number): void {
    this.callbacks.delete(handle);
  }

  now(): number {
    return this.time;
  }

  get pendingFrames(): number {
    return this.callbacks.size;
  }

  frame(advanceMs = 16): void {
    this.time += advanceMs;
    const callbacks = [...this.callbacks.values()];
    this.callbacks.clear();
    for (const callback of callbacks) callback();
  }
}
