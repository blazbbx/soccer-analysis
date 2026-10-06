import { useQuery } from "@tanstack/react-query";
import { customInstance } from "./axiosInstance";
import type { TeamResponse } from "./generated/model";
import { useView } from "../context/ViewContext";

export const getRoleTeamsQueryKey = (role: string) => ["/api/teams", role] as const;

export const useRoleTeams = (enabled = true) => {
  const { activeView } = useView();
  const apiRole = activeView.toUpperCase();
  return useQuery({
    queryKey: getRoleTeamsQueryKey(activeView),
    queryFn: () => customInstance<TeamResponse[]>(`/api/teams?role=${encodeURIComponent(apiRole)}`, { method: "GET" }),
    enabled,
  });
};
