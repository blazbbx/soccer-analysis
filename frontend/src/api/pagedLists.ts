import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { customInstance } from "./axiosInstance";
import { getListClubsQueryKey } from "./generated/clubs/clubs";
import { getGetAllMatchesQueryKey, getGetClipsQueryKey } from "./generated/match-controller/match-controller";
import { getGetAllUsersQueryKey } from "./generated/user-controller/user-controller";
import type { ClubResponse, ClipResponse, MatchResponse, UserResponse } from "./generated/model";
import { useView } from "../context/ViewContext";
import {
  CLUB_USERS_PAGE_SIZE,
  LIST_PAGE_SIZE,
  pageSearch,
  type CupListItem,
  type PageResponse,
  type UserPageResponse,
} from "./paging";

export const CUPS_PAGE_KEY = ["/api/cups"] as const;

export function useListPage(resetKey: string) {
  const [page, setPage] = useState(1);
  useEffect(() => {
    setPage(1);
  }, [resetKey]);
  return [page, setPage] as const;
}

export function useRetreatEmptyPage(
  page: number,
  itemCount: number | undefined,
  totalPages: number | undefined,
  setPage: (page: number) => void,
) {
  useEffect(() => {
    if (itemCount == null || totalPages == null) return;
    if (page > 1 && itemCount === 0) {
      setPage(Math.max(1, totalPages));
    }
  }, [page, itemCount, totalPages, setPage]);
}

export function usePagedMatches(options: {
  page: number;
  size?: number;
  view?: string;
  teamIds?: string[];
  enabled?: boolean;
}) {
  const size = options.size ?? LIST_PAGE_SIZE;
  const teamIds = options.teamIds ?? [];
  const view = options.view ?? "";
  return useQuery({
    queryKey: [...getGetAllMatchesQueryKey(), { view, page: options.page, size, teamIds }],
    queryFn: ({ signal }) =>
      customInstance<PageResponse<MatchResponse>>(
        `/api/matches${pageSearch({
          view: view || undefined,
          page: options.page,
          size,
          teamIds: teamIds.length > 0 ? teamIds : undefined,
        })}`,
        { method: "GET", signal },
      ),
    enabled: options.enabled ?? true,
  });
}

export function usePagedUsers(page: number, size = LIST_PAGE_SIZE, enabled = true, query = "") {
  const q = query.trim();
  return useQuery({
    queryKey: [...getGetAllUsersQueryKey(), { page, size, q }],
    queryFn: ({ signal }) =>
      customInstance<UserPageResponse<UserResponse>>(
        `/api/users${pageSearch({ page, size, q: q || undefined })}`,
        { method: "GET", signal },
      ),
    enabled,
  });
}

export function usePagedClips(matchId: string, page: number, size = LIST_PAGE_SIZE) {
  return useQuery({
    queryKey: [...getGetClipsQueryKey(matchId), { page, size }],
    queryFn: ({ signal }) =>
      customInstance<PageResponse<ClipResponse>>(
        `/api/matches/${matchId}/clips${pageSearch({ page, size })}`,
        { method: "GET", signal },
      ),
    enabled: Boolean(matchId),
  });
}

export function usePagedClubs(page: number, size = LIST_PAGE_SIZE, enabled = true) {
  return useQuery({
    queryKey: [...getListClubsQueryKey(), { page, size }],
    queryFn: ({ signal }) =>
      customInstance<PageResponse<ClubResponse>>(
        `/api/clubs${pageSearch({ page, size })}`,
        { method: "GET", signal },
      ),
    enabled,
  });
}

export function usePagedCups(options: {
  page: number;
  size?: number;
  teamIds?: string[];
  enabled?: boolean;
}) {
  const { activeView } = useView();
  const view = activeView?.toUpperCase() ?? "";
  const size = options.size ?? LIST_PAGE_SIZE;
  const teamIds = options.teamIds ?? [];
  return useQuery({
    queryKey: [...CUPS_PAGE_KEY, { view, page: options.page, size, teamIds }],
    queryFn: ({ signal }) =>
      customInstance<PageResponse<CupListItem>>(
        `/api/cups${pageSearch({
          view: view || undefined,
          page: options.page,
          size,
          teamIds: teamIds.length > 0 ? teamIds : undefined,
        })}`,
        { method: "GET", signal },
      ),
    enabled: (options.enabled ?? true) && Boolean(activeView),
  });
}

export function usePagedClubUsers(clubId: string, page: number, query: string, size = CLUB_USERS_PAGE_SIZE) {
  const q = query.trim();
  return useQuery({
    queryKey: ["clubs", clubId, "users", { page, size, q }],
    queryFn: ({ signal }) =>
      customInstance<PageResponse<UserResponse>>(
        `/api/clubs/${clubId}/users${pageSearch({ page, size, q: q || undefined })}`,
        { method: "GET", signal },
      ),
    enabled: Boolean(clubId),
  });
}
