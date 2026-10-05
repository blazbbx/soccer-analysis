import { describe, expect, it, vi } from 'vitest';
import type { ClipDrawing } from '../types/drawings';
import { createAnnotationStore, inputModeOf } from './annotationStore';

const stroke = (id: string, view: 'video' | 'pitch'): ClipDrawing => ({
  id,
  type: 'static',
  view,
  tool: 'pen',
  color: '#fff',
  points: [{ x: 0, y: 0 }, { x: 1, y: 1 }],
});

describe('annotationStore', () => {
  it('az alapállapot passzív, rajz nélkül', () => {
    const store = createAnnotationStore();
    const state = store.getState();
    expect(state.activeTool).toBe('none');
    expect(state.followMode).toBe(false);
    expect(state.selectedPlayerId).toBeNull();
    expect(state.drawings).toEqual([]);
    expect(inputModeOf(state)).toBe('passive');
  });

  it('a követő mód kikapcsolása törli a kijelölést, bekapcsolása megtartja', () => {
    const store = createAnnotationStore();
    store.setFollowMode(true);
    store.selectPlayer(7);
    store.setFollowMode(true);
    expect(store.getState().selectedPlayerId).toBe(7);
    store.setFollowMode(false);
    expect(store.getState().selectedPlayerId).toBeNull();
  });

  it('a visszavonás és a törlés csak az adott nézet rajzait érinti', () => {
    const store = createAnnotationStore();
    store.addDrawing(stroke('v1', 'video'));
    store.addDrawing(stroke('p1', 'pitch'));
    store.addDrawing(stroke('v2', 'video'));

    store.undo('video');
    expect(store.getState().drawings.map((d) => d.id)).toEqual(['v1', 'p1']);

    store.clear('pitch');
    expect(store.getState().drawings.map((d) => d.id)).toEqual(['v1']);
  });

  it('üres visszavonásnál és törlésnél nem értesít', () => {
    const store = createAnnotationStore();
    const listener = vi.fn();
    store.subscribe(listener);
    store.undo('video');
    store.clear('pitch');
    expect(listener).not.toHaveBeenCalled();
  });

  it('értesít változáskor, a leiratkozás működik, és az állapot csak változáskor lesz új objektum', () => {
    const store = createAnnotationStore();
    const listener = vi.fn();
    const unsubscribe = store.subscribe(listener);
    const before = store.getState();
    store.setColor('#00f');
    expect(store.getState()).not.toBe(before);
    unsubscribe();
    store.setColor('#0f0');
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('a felvétel nincs a store-ban: a rajzok megmaradnak (D6)', () => {
    const store = createAnnotationStore();
    store.addDrawing(stroke('v1', 'video'));
    store.setTool('pen');
    store.setTool('none');
    expect(store.getState().drawings).toHaveLength(1);
  });
});

describe('inputModeOf', () => {
  const base = createAnnotationStore().getState();

  it('eszközzel és követő mód nélkül szabad rajz', () => {
    expect(inputModeOf({ ...base, activeTool: 'pen' })).toBe('free-draw');
  });

  it('követő módban kijelölés nélkül a kijelölés a feladat, az eszköztől függetlenül', () => {
    expect(inputModeOf({ ...base, followMode: true })).toBe('select');
    expect(inputModeOf({ ...base, followMode: true, activeTool: 'arrow' })).toBe('select');
  });

  it('kijelölt játékosnál eszközzel követő rajz, eszköz nélkül passzív', () => {
    expect(inputModeOf({ ...base, followMode: true, selectedPlayerId: 3, activeTool: 'circle' })).toBe(
      'follow-draw',
    );
    expect(inputModeOf({ ...base, followMode: true, selectedPlayerId: 3 })).toBe('passive');
  });
});
