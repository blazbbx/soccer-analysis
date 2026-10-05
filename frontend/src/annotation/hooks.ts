import { useContext, useSyncExternalStore } from 'react';
import { AnnotationContext } from './annotationContext';
import type { AnnotationActions, AnnotationState, AnnotationStore } from './annotationStore';

export function useAnnotationStore(): AnnotationStore {
  const store = useContext(AnnotationContext);
  if (!store) throw new Error('Az annotation hookok az AnnotationProvideren belül használhatók');
  return store;
}

// A műveleteket metódusként kell hívni (actions.undo(view)); a store zárványokból áll, destrukturálható is.
export const useAnnotationActions = (): AnnotationActions => useAnnotationStore();

export function useAnnotationState(): AnnotationState {
  const store = useAnnotationStore();
  return useSyncExternalStore(store.subscribe, store.getState);
}
