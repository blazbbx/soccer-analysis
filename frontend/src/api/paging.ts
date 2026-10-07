export type PageResponse<T> = {
  items: T[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
};

export type UserPageResponse<T> = PageResponse<T> & {
  adminCount: number;
};

export type CupListItem = {
  id: string;
  name?: string;
  createdAt?: string;
  createdById?: string;
  createdByFirstName?: string;
  createdByLastName?: string;
  teamCount: number;
  matchCount: number;
};

export const LIST_PAGE_SIZE = 20;
export const CLUB_USERS_PAGE_SIZE = 10;
export const PICKER_PAGE_SIZE = 50;

export function pageSearch(params: {
  view?: string;
  page?: number;
  size?: number;
  teamIds?: string[];
  q?: string;
}): string {
  const search = new URLSearchParams();
  if (params.view) search.set("view", params.view);
  if (params.page != null) search.set("page", String(params.page));
  if (params.size != null) search.set("size", String(params.size));
  for (const teamId of params.teamIds ?? []) {
    if (teamId) search.append("teamId", teamId);
  }
  const query = params.q?.trim();
  if (query) search.set("q", query);
  const text = search.toString();
  return text ? `?${text}` : "";
}
