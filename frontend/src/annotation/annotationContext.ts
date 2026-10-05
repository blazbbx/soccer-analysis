import { createContext } from 'react';
import type { AnnotationStore } from './annotationStore';

export const AnnotationContext = createContext<AnnotationStore | null>(null);
