import type { ClipDrawing, DrawingTool, DrawingView } from '../types/drawings';

export type ActiveTool = DrawingTool | 'none';

export interface AnnotationState {
  readonly activeTool: ActiveTool;
  readonly color: string;
  readonly followMode: boolean;
  readonly selectedPlayerId: number | null;
  readonly drawings: readonly ClipDrawing[];
}

export interface AnnotationActions {
  setTool(tool: ActiveTool): void;
  setColor(color: string): void;
  setFollowMode(active: boolean): void;
  selectPlayer(playerId: number | null): void;
  addDrawing(drawing: ClipDrawing): void;
  undo(view: DrawingView): void;
  clear(view: DrawingView): void;
}

export interface AnnotationStore extends AnnotationActions {
  getState(): AnnotationState;
  subscribe(cb: () => void): () => void;
}

// Mit csinál a rajzfelület egy kattintással/húzással (terv: 4.1, az állapot-táblázat).
export type InputMode = 'passive' | 'free-draw' | 'select' | 'follow-draw';

export function inputModeOf(state: AnnotationState): InputMode {
  if (state.followMode) {
    if (state.selectedPlayerId === null) return 'select';
    return state.activeTool === 'none' ? 'passive' : 'follow-draw';
  }
  return state.activeTool === 'none' ? 'passive' : 'free-draw';
}

const INITIAL_STATE: AnnotationState = {
  activeTool: 'none',
  color: '#f44336',
  followMode: false,
  selectedPlayerId: null,
  drawings: [],
};

// A rajzok a felhasználó kifejezett törléséig maradnak (D6); a felvétel nem érinti őket.
export function createAnnotationStore(initial: AnnotationState = INITIAL_STATE): AnnotationStore {
  let state = initial;
  const listeners = new Set<() => void>();

  const update = (next: AnnotationState) => {
    if (next === state) return;
    state = next;
    for (const listener of [...listeners]) listener();
  };

  return {
    getState: () => state,
    subscribe(cb) {
      listeners.add(cb);
      return () => {
        listeners.delete(cb);
      };
    },
    setTool: (activeTool) => update({ ...state, activeTool }),
    setColor: (color) => update({ ...state, color }),
    setFollowMode: (followMode) =>
      update({ ...state, followMode, selectedPlayerId: followMode ? state.selectedPlayerId : null }),
    selectPlayer: (selectedPlayerId) => update({ ...state, selectedPlayerId }),
    addDrawing: (drawing) => update({ ...state, drawings: [...state.drawings, drawing] }),
    undo(view) {
      const index = state.drawings.findLastIndex((d) => d.view === view);
      if (index === -1) return;
      update({ ...state, drawings: state.drawings.filter((_, i) => i !== index) });
    },
    clear(view) {
      if (!state.drawings.some((d) => d.view === view)) return;
      update({ ...state, drawings: state.drawings.filter((d) => d.view !== view) });
    },
  };
}
