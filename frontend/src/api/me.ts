import { customInstance } from "./axiosInstance";
import { type Role } from "../types/roles";

export const MY_VIEWS_QUERY_KEY = ["me-views"] as const;

export interface MeViewsResponse {
  primaryRole: string;
  hats: string[];
  clubAdminClubIds: string[];
  coachTeamIds: string[];
  parentProfileIds: string[];
  playerTeamIds: string[];
  fanTeamIds: string[];
}

export const getMeViews = () =>
  customInstance<MeViewsResponse>("/api/me/views", { method: "GET" });

export const membershipCountForHat = (
  hat: Role,
  views: MeViewsResponse | undefined,
): number | undefined => {
  if (!views) return undefined;
  switch (hat) {
    case "club_admin":
      return views.clubAdminClubIds?.length ?? 0;
    case "coach":
      return views.coachTeamIds?.length ?? 0;
    case "parent":
      return views.parentProfileIds?.length ?? 0;
    case "player":
      return views.playerTeamIds?.length ?? 0;
    case "fan":
      return views.fanTeamIds?.length ?? 0;
    default:
      return undefined;
  }
};
