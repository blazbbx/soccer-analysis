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
const LEGACY_STORAGE_KEY = "selectedChildProfileId";
const storageKey = (userId: string) => `selectedChildProfileId:${userId}`;

interface ChildrenContextType {
  childrenList: ChildResponse[];
  selected: ChildResponse | null;
  select: (profileId: string) => void;
  isLoading: boolean;
}

const ChildrenContext = createContext<ChildrenContextType | undefined>(undefined);

const readStoredChild = (userId: string) => {
  const own = localStorage.getItem(storageKey(userId));
  if (own) return own;
  return localStorage.getItem(LEGACY_STORAGE_KEY);
};

export const ChildrenProvider = ({ children }: { children: ReactNode }) => {
  const { user } = useAuth();
  const { hats } = useView();
  const userId = user?.id;
  const isParent = Boolean(userId && hats.includes(ROLES.PARENT));
  const { data, isLoading } = useQuery({
    queryKey: MY_CHILDREN_QUERY_KEY,
    queryFn: listMyChildren,
    enabled: isParent,
  });
  const childrenList = isParent ? (data ?? []) : [];
  const [selectedId, setSelectedId] = useState<string | null>(null);

  useEffect(() => {
    if (!userId) {
      setSelectedId(null);
      return;
    }
    setSelectedId(readStoredChild(userId));
  }, [userId]);

  useEffect(() => {
    if (!userId || !isParent || isLoading) return;
    const key = storageKey(userId);
    localStorage.removeItem(LEGACY_STORAGE_KEY);
    if (childrenList.length === 0) {
      setSelectedId(null);
      localStorage.removeItem(key);
      return;
    }
    const stillThere = selectedId != null && childrenList.some((child) => child.profileId === selectedId);
    if (stillThere) {
      localStorage.setItem(key, selectedId);
      return;
    }
    const next = childrenList[0].profileId;
    setSelectedId(next);
    localStorage.setItem(key, next);
  }, [userId, isParent, isLoading, childrenList, selectedId]);

  const select = useCallback((profileId: string) => {
    setSelectedId(profileId);
    if (userId) {
      localStorage.setItem(storageKey(userId), profileId);
    }
  }, [userId]);

  const selected = childrenList.find((child) => child.profileId === selectedId) ?? null;

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
