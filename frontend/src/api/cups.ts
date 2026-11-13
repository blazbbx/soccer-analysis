import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { customInstance } from "./axiosInstance";
import { useView } from "../context/ViewContext";
import { CUPS_PAGE_KEY } from "./pagedLists";
import type { CupResponse, GetCupParams, GetCupView } from "./generated/model";

/** The cup endpoints that the generated client does not have yet (rename, delete, clear score, match page link). */

export type MatchCupLink = {
  cupId: string;
  cupName: string;
  cupMatchId: string;
};

/** The active view as the `view` query param of the cup read endpoints (5.3). */
export const useCupViewParams = (): GetCupParams | undefined => {
  const { activeView } = useView();
  return activeView ? { view: activeView.toUpperCase() as GetCupView } : undefined;
};

/** Every cup query starts with "/api/cups": list, detail, teams, matches, standings. */
export const invalidateCups = (queryClient: QueryClient) =>
  queryClient.invalidateQueries({
    predicate: (query) => typeof query.queryKey[0] === "string" && query.queryKey[0].startsWith("/api/cups"),
  });

export const useRenameCup = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ cupId, name }: { cupId: string; name: string }) =>
      customInstance<CupResponse>(`/api/cups/${cupId}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: { name },
      }),
    onSuccess: () => invalidateCups(queryClient),
  });
};

export const useDeleteCup = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (cupId: string) => customInstance<void>(`/api/cups/${cupId}`, { method: "DELETE" }),
    onSuccess: (_, cupId) => {
      // The deleted cup's own queries are dropped, not refetched (that would only give a 404).
      queryClient.removeQueries({
        predicate: (query) => typeof query.queryKey[0] === "string" && query.queryKey[0].startsWith(`/api/cups/${cupId}`),
      });
      return queryClient.invalidateQueries({ queryKey: CUPS_PAGE_KEY });
    },
  });
};

export const useClearCupScore = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ cupId, matchId }: { cupId: string; matchId: string }) =>
      customInstance<void>(`/api/cups/${cupId}/matches/${matchId}/score`, { method: "DELETE" }),
    // D2: also after a no-op, so a second manager sees the current state.
    onSettled: () => invalidateCups(queryClient),
  });
};

/** 2.3: the cups this analysed match belongs to. */
export const useMatchCupLinks = (matchId: string) => {
  const params = useCupViewParams();
  return useQuery({
    queryKey: ["/api/matches", matchId, "cup-matches", params ?? {}],
    queryFn: ({ signal }) =>
      customInstance<MatchCupLink[]>(
        `/api/matches/${matchId}/cup-matches${params?.view ? `?view=${params.view}` : ""}`,
        { method: "GET", signal },
      ),
    enabled: matchId.length > 0,
  });
};
