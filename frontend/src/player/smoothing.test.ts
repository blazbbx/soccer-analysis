import { describe, expect, it } from 'vitest';
import type { TrackingEntry } from '../types/trackingData';
import { DEFAULT_SMOOTHING, smoothPlayerPositions } from './smoothing';

const at = (frame: number, player_id: number, tx: number, ty: number): TrackingEntry => ({
  frame,
  player_id,
  team: 'home',
  x1: 1,
  y1: 2,
  x2: 11,
  y2: 32,
  tx,
  ty,
});

const range = (from: number, to: number) => Array.from({ length: to - from + 1 }, (_, i) => from + i);

const rms = (values: number[]) => Math.sqrt(values.reduce((sum, v) => sum + v * v, 0) / values.length);

// Egyenletes mozgás + kockánként előjelet váltó "rezgés".
const line = (f: number) => ({ x: 5 + 0.1 * f, y: 30 - 0.05 * f });
const jittery = (f: number, amp = 0.3) => at(f, 1, line(f).x + (f % 2 ? amp : -amp), line(f).y + (f % 2 ? -amp : amp));

describe('smoothPlayerPositions', () => {
  it('az egyenletes mozgást pontosan visszaadja, a track szélén is (nincs torzítás)', () => {
    const entries = range(0, 100).map((f) => at(f, 1, line(f).x, line(f).y));
    const out = smoothPlayerPositions(entries, 50);
    out.forEach((entry, i) => {
      expect(entry.tx).toBeCloseTo(entries[i].tx as number, 9);
      expect(entry.ty).toBeCloseTo(entries[i].ty as number, 9);
    });
  });

  it('a hiányzó képkockák mellett is pontos az egyenletes mozgásnál (a súly a képkocka-távolságtól függ)', () => {
    const frames = [...range(0, 20), ...range(24, 40), ...range(70, 90)];
    const entries = frames.map((f) => at(f, 1, line(f).x, line(f).y));
    const out = smoothPlayerPositions(entries, 50);
    out.forEach((entry, i) => expect(entry.tx).toBeCloseTo(entries[i].tx as number, 9));
  });

  it('a kockánkénti rezgést jelentősen csökkenti', () => {
    const entries = range(0, 200).map((f) => jittery(f));
    const out = smoothPlayerPositions(entries, 50);
    const rawError = rms(entries.map((e) => (e.tx as number) - line(e.frame).x));
    const smoothedError = rms(out.map((e) => (e.tx as number) - line(e.frame).x));
    expect(rawError).toBeCloseTo(0.3, 6);
    expect(smoothedError).toBeLessThan(0.05 * rawError);
  });

  it('a trackeket nem keveri össze: más player_id más track', () => {
    const entries = range(0, 60).flatMap((f) => [at(f, 1, 10, 10), at(f, 2, 90, 60)]);
    const out = smoothPlayerPositions(entries, 50);
    out.forEach((entry) => {
      expect(entry.tx).toBeCloseTo(entry.player_id === 1 ? 10 : 90, 9);
      expect(entry.ty).toBeCloseTo(entry.player_id === 1 ? 10 : 60, 9);
    });
  });

  it('az ablakon túl lévő (id-váltás utáni) pontokat nem érinti', () => {
    // Két, egymástól 1 mp-re lévő ugyanazon id-s pont: egyik sem lát a másikból érdemi súlyt.
    const entries = [at(0, 1, 10, 10), at(50, 1, 80, 40)];
    const out = smoothPlayerPositions(entries, 50);
    expect(out[0].tx).toBeCloseTo(10, 9);
    expect(out[1].tx).toBeCloseTo(80, 9);
  });

  it('a nyers értéket megtartja, a dobozt és a többi mezőt nem bántja', () => {
    const entries = range(0, 30).map((f) => jittery(f));
    const out = smoothPlayerPositions(entries, 50);
    out.forEach((entry, i) => {
      expect(entry.rawTx).toBe(entries[i].tx);
      expect(entry.rawTy).toBe(entries[i].ty);
      expect({ ...entry, tx: undefined, ty: undefined, rawTx: undefined, rawTy: undefined }).toEqual({
        ...entries[i],
        tx: undefined,
        ty: undefined,
        rawTx: undefined,
        rawTy: undefined,
      });
    });
  });

  it('a tx/ty nélküli és az id nélküli (player_id < 0) sort változatlanul hagyja', () => {
    const noPosition: TrackingEntry = { frame: 3, player_id: 1, x1: 0, y1: 0, x2: 1, y2: 1 };
    const noId = at(3, -1, 40, 20);
    const entries = [at(2, 1, 5, 5), noPosition, noId, at(4, 1, 5, 5)];
    const out = smoothPlayerPositions(entries, 50);
    expect(out[1]).toBe(noPosition);
    expect(out[2]).toBe(noId);
  });

  it('a bemenet sorrendjét és hosszát megtartja, rendezetlen bemenetnél is ugyanazt adja', () => {
    const sorted = range(0, 80).map((f) => jittery(f));
    const shuffled = [...sorted].reverse();
    const fromSorted = smoothPlayerPositions(sorted, 50);
    const fromShuffled = smoothPlayerPositions(shuffled, 50);
    expect(fromShuffled).toHaveLength(shuffled.length);
    fromShuffled.forEach((entry, i) => {
      expect(entry.frame).toBe(shuffled[i].frame);
      const twin = fromSorted.find((e) => e.frame === entry.frame);
      expect(entry.tx).toBeCloseTo(twin?.tx as number, 9);
    });
  });

  it('nem módosítja a bemenetet', () => {
    const entries = range(0, 40).map((f) => jittery(f));
    const snapshot = JSON.stringify(entries);
    smoothPlayerPositions(entries, 50);
    expect(JSON.stringify(entries)).toBe(snapshot);
  });

  it('egyetlen pontból álló trackre nem csinál semmit', () => {
    const out = smoothPlayerPositions([at(7, 3, 12.5, 40.25)], 50);
    expect(out[0].tx).toBe(12.5);
    expect(out[0].ty).toBe(40.25);
  });

  it('érvénytelen fps-nél vagy szórásnál nem simít', () => {
    const entries = range(0, 20).map((f) => jittery(f));
    expect(smoothPlayerPositions(entries, 0)).toBe(entries);
    expect(smoothPlayerPositions(entries, Number.NaN)).toBe(entries);
    expect(smoothPlayerPositions(entries, 50, { sigmaSeconds: 0 })).toBe(entries);
  });

  it('a szórás másodpercben van: ugyanaz a valódi mozgás 25 és 50 fps-nél közel azonos simítást kap', () => {
    // Ugrás a 2. másodpercben (itt látszik a legjobban a simítás mértéke), 50 és 25 fps-en mintavételezve.
    const step = (fps: number) => range(0, 4 * fps).map((f) => at(f, 1, f / fps < 2 ? 10 : 20, 30));
    const at50 = smoothPlayerPositions(step(50), 50);
    const at25 = smoothPlayerPositions(step(25), 25);
    // Ugyanaz a pillanat: 2,08 s = 50 fps-en a 104., 25 fps-en az 52. kocka.
    const v50 = at50.find((e) => e.frame === 104)?.tx as number;
    const v25 = at25.find((e) => e.frame === 52)?.tx as number;
    expect(v50).toBeGreaterThan(10); // tényleg simított (nem a nyers 20)
    expect(v50).toBeLessThan(20);
    expect(Math.abs(v50 - v25)).toBeLessThan(0.3);
  });

  it('az alapértelmezett szórás pozitív és másodpercben értendő', () => {
    expect(DEFAULT_SMOOTHING.sigmaSeconds).toBeGreaterThan(0);
    expect(DEFAULT_SMOOTHING.sigmaSeconds).toBeLessThan(2);
  });
});
