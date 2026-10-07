import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { customInstance } from "./axiosInstance";
import { useView } from "../context/ViewContext";

export interface TeamCatalogItem {
  id?: string;
  name?: string;
  shortName?: string;
  logoUrl?: string;
  clubId?: string;
  clubName?: string;
  playerCount?: number;
  coachCount?: number;
}

export interface TeamCatalogPage {
  items: TeamCatalogItem[];
  total: number;
  partial: boolean;
  page: number;
  size: number;
  totalPages: number;
}

export const TEAM_CATALOG_KEY = ["/api/teams/catalog"] as const;

export const useTeamCatalog = (
  query = "",
  enabled = true,
  keepPrevious = false,
  page?: number,
  size?: number,
) => {
  const { activeView } = useView();
  const apiRole = activeView?.toUpperCase() ?? "";
  const q = query.trim();
  return useQuery({
    queryKey: [...TEAM_CATALOG_KEY, apiRole, q, page ?? null, size ?? null],
    queryFn: () => {
      const params = new URLSearchParams({ role: apiRole });
      if (q) params.set("q", q);
      if (page != null) params.set("page", String(page));
      if (size != null) params.set("size", String(size));
      return customInstance<TeamCatalogPage>(`/api/teams/catalog?${params.toString()}`, { method: "GET" });
    },
    enabled: enabled && Boolean(activeView),
    placeholderData: keepPrevious ? keepPreviousData : undefined,
  });
};

export const useRoleTeams = (enabled = true) => useTeamCatalog("", enabled);
