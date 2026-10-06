import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { HAT_RANK, ROLES, type Role } from "../types/roles";
import { useAuth } from "./AuthContext";

interface ViewContextType {
  hats: Role[];
  activeView: Role;
  setActiveView: (hat: Role) => void;
}

const ViewContext = createContext<ViewContextType | undefined>(undefined);

const storageKey = (userId: string) => `soccer.activeView:${userId}`;

const pickDefaultHat = (hats: Role[]): Role =>
  HAT_RANK.find((hat) => hats.includes(hat)) ?? hats[0] ?? ROLES.FAN;

const readStored = (userId: string, hats: Role[]): Role => {
  const raw = localStorage.getItem(storageKey(userId));
  if (raw && hats.includes(raw as Role)) {
    return raw as Role;
  }
  return pickDefaultHat(hats);
};

const viewKeyFor = (userId: string | undefined, hats: Role[]) =>
  userId ? `${userId}:${hats.join("\0")}` : "";

export const ViewProvider = ({ children }: { children: ReactNode }) => {
  const { user } = useAuth();
  const hats = useMemo<Role[]>(() => {
    if (!user) return [];
    return user.roles?.length ? user.roles : [user.role];
  }, [user]);
  const viewKey = viewKeyFor(user?.id, hats);
  const [activeView, setActiveViewState] = useState<Role>(() =>
    user ? readStored(user.id, hats) : ROLES.FAN,
  );
  const [hydratedKey, setHydratedKey] = useState(viewKey);

  if (viewKey !== hydratedKey) {
    setHydratedKey(viewKey);
    setActiveViewState(user ? readStored(user.id, hats) : ROLES.FAN);
  }

  const setActiveView = useCallback(
    (hat: Role) => {
      if (!hats.includes(hat)) return;
      setActiveViewState(hat);
      if (user) {
        localStorage.setItem(storageKey(user.id), hat);
      }
    },
    [hats, user],
  );

  const value = useMemo(
    () => ({ hats, activeView, setActiveView }),
    [hats, activeView, setActiveView],
  );

  return <ViewContext.Provider value={value}>{children}</ViewContext.Provider>;
};

export const useView = () => {
  const context = useContext(ViewContext);
  if (context === undefined) {
    throw new Error("A useView hookot csak a ViewProvider-en belül lehet használni!");
  }
  return context;
};
