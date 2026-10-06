import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { useQuery } from "@tanstack/react-query";
import { listMyChildren, type ChildResponse } from "../api/children";
import { ROLES } from "../types/roles";
import { useAuth } from "./AuthContext";
import { useView } from "./ViewContext";

export const MY_CHILDREN_QUERY_KEY = ["my-children"] as const;
const STORAGE_KEY = "selectedChildProfileId";

interface ChildrenContextType {
  childrenList: ChildResponse[];
  selected: ChildResponse | null;
  select: (profileId: string) => void;
  isLoading: boolean;
}

const ChildrenContext = createContext<ChildrenContextType | undefined>(undefined);

export const ChildrenProvider = ({ children }: { children: ReactNode }) => {
  const { user } = useAuth();
  const { hats } = useView();
  const isParent = Boolean(user && hats.includes(ROLES.PARENT));
  const { data, isLoading } = useQuery({
    queryKey: MY_CHILDREN_QUERY_KEY,
    queryFn: listMyChildren,
    enabled: isParent,
  });
  const childrenList = isParent ? (data ?? []) : [];
  const [selectedId, setSelectedId] = useState<string | null>(() =>
    localStorage.getItem(STORAGE_KEY),
  );

  const selected =
    childrenList.find((child) => child.profileId === selectedId) ?? childrenList[0] ?? null;

  useEffect(() => {
    if (!selected) return;
    if (selected.profileId === selectedId) return;
    setSelectedId(selected.profileId);
    localStorage.setItem(STORAGE_KEY, selected.profileId);
  }, [selected, selectedId]);

  const select = useCallback((profileId: string) => {
    setSelectedId(profileId);
    localStorage.setItem(STORAGE_KEY, profileId);
  }, []);

  return (
    <ChildrenContext.Provider
      value={{ childrenList, selected, select, isLoading: isParent && isLoading }}
    >
      {children}
    </ChildrenContext.Provider>
  );
};

export const useChildren = () => {
  const context = useContext(ChildrenContext);
  if (!context) {
    throw new Error("useChildren must be used within ChildrenProvider");
  }
  return context;
};
