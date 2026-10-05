import { useState, type ReactNode } from 'react';
import { AnnotationContext } from './annotationContext';
import { createAnnotationStore } from './annotationStore';

export function AnnotationProvider({ children }: { children: ReactNode }) {
  const [store] = useState(() => createAnnotationStore());
  return <AnnotationContext.Provider value={store}>{children}</AnnotationContext.Provider>;
}
