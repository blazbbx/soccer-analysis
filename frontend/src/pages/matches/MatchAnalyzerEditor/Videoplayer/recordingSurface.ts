// Amit egy nézet a felvételnek átad. A videónézetnek nincs scene-vászna (a videót a backend illeszti hozzá).
export interface RecordingSurfaceHandle {
  readonly sceneCanvas?: HTMLCanvasElement | null;
  readonly contentCanvas: HTMLCanvasElement | null;
}
