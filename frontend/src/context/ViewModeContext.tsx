import React, { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';

export type ViewMode = 'video' | 'pitch';

interface ViewModeContextType {
  viewMode: ViewMode;
  toggleViewMode: () => void;
}

const ViewModeContext = createContext<ViewModeContextType | undefined>(undefined);

// Az aktív nézet közös állapot: az elrendezés, az Annotation és a felvétel is ettől függ.
export const ViewModeProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [viewMode, setViewMode] = useState<ViewMode>('video');
  const toggleViewMode = useCallback(() => setViewMode((v) => (v === 'video' ? 'pitch' : 'video')), []);
  const value = useMemo(() => ({ viewMode, toggleViewMode }), [viewMode, toggleViewMode]);
  return <ViewModeContext.Provider value={value}>{children}</ViewModeContext.Provider>;
};

export const useViewMode = (): ViewModeContextType => {
  const ctx = useContext(ViewModeContext);
  if (!ctx) throw new Error('useViewMode must be used within ViewModeProvider');
  return ctx;
};
