import React, {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import type { LabelItemConfig } from "../constants/labels";

export interface PlacedLabel {
  id: string;
  config: LabelItemConfig;
  time: number;
}

interface LabelsContextType {
  labels: PlacedLabel[];
  addLabel: (label: Omit<PlacedLabel, "id">) => void;
  deleteLabel: (id: string) => void;
  updateLabel: (id: string, config: LabelItemConfig) => void;
  initLabels: (labels: PlacedLabel[]) => void;
}

const LabelsContext = createContext<LabelsContextType | undefined>(undefined);

export const LabelsProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [labels, setLabels] = useState<PlacedLabel[]>([]);
  const labelIdCounterRef = React.useRef(0);

  const addLabel = useCallback((label: Omit<PlacedLabel, "id">) => {
    const id = `label-${labelIdCounterRef.current++}`;
    setLabels((ls) => [...ls, { ...label, id }]);
  }, []);

  const deleteLabel = useCallback((id: string) => {
    setLabels((ls) => ls.filter((l) => l.id !== id));
  }, []);

  const updateLabel = useCallback((id: string, config: LabelItemConfig) => {
    setLabels((ls) => ls.map((l) => (l.id === id ? { ...l, config } : l)));
  }, []);

  const initLabels = useCallback((incoming: PlacedLabel[]) => setLabels(incoming), []);

  const value = useMemo(
    () => ({ labels, addLabel, deleteLabel, updateLabel, initLabels }),
    [labels, addLabel, deleteLabel, updateLabel, initLabels],
  );

  return <LabelsContext.Provider value={value}>{children}</LabelsContext.Provider>;
};

export const useLabels = (): LabelsContextType => {
  const ctx = useContext(LabelsContext);
  if (!ctx) throw new Error("useLabels must be used within LabelsProvider");
  return ctx;
};
