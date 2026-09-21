import { useMutation, useQuery } from "@tanstack/react-query";
import type {
  UseMutationOptions,
  UseQueryOptions,
} from "@tanstack/react-query";
import type {
  ClubResponse,
  CreateClubRequest,
  TeamResponse,
  UpdateClubRequest,
} from "../model";
import { customInstance } from "../../axiosInstance";

export const getListClubsUrl = () => "/api/clubs";

export const listClubs = async (): Promise<ClubResponse[]> => {
  return customInstance<ClubResponse[]>(getListClubsUrl(), { method: "GET" });
};

export const getListClubsQueryKey = () => [getListClubsUrl()] as const;

export const useListClubs = (
  options?: { query?: Partial<UseQueryOptions<ClubResponse[]>> },
) => {
  return useQuery({
    queryKey: getListClubsQueryKey(),
    queryFn: listClubs,
    ...options?.query,
  });
};

export const getGetClubUrl = (id: string) => `/api/clubs/${id}`;

export const getClub = async (id: string): Promise<ClubResponse> => {
  return customInstance<ClubResponse>(getGetClubUrl(id), { method: "GET" });
};

export const getGetClubQueryKey = (id: string) => [getGetClubUrl(id)] as const;

export const useGetClub = (
  id: string,
  options?: { query?: Partial<UseQueryOptions<ClubResponse>> },
) => {
  return useQuery({
    queryKey: getGetClubQueryKey(id),
    queryFn: () => getClub(id),
    enabled: Boolean(id),
    ...options?.query,
  });
};

export const getListClubTeamsUrl = (id: string) => `/api/clubs/${id}/teams`;

export const listClubTeams = async (id: string): Promise<TeamResponse[]> => {
  return customInstance<TeamResponse[]>(getListClubTeamsUrl(id), { method: "GET" });
};

export const getListClubTeamsQueryKey = (id: string) =>
  [getListClubTeamsUrl(id)] as const;

export const useListClubTeams = (
  id: string,
  options?: { query?: Partial<UseQueryOptions<TeamResponse[]>> },
) => {
  return useQuery({
    queryKey: getListClubTeamsQueryKey(id),
    queryFn: () => listClubTeams(id),
    enabled: Boolean(id),
    ...options?.query,
  });
};

export const createClub = async (
  createClubRequest: CreateClubRequest,
): Promise<ClubResponse> => {
  return customInstance<ClubResponse>(getListClubsUrl(), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(createClubRequest),
  });
};

export const useCreateClub = (
  options?: UseMutationOptions<ClubResponse, unknown, { data: CreateClubRequest }>,
) => {
  return useMutation({
    mutationFn: ({ data }) => createClub(data),
    ...options,
  });
};

export const updateClub = async (
  id: string,
  updateClubRequest: UpdateClubRequest,
): Promise<ClubResponse> => {
  return customInstance<ClubResponse>(getGetClubUrl(id), {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(updateClubRequest),
  });
};

export const useUpdateClub = (
  options?: UseMutationOptions<
    ClubResponse,
    unknown,
    { id: string; data: UpdateClubRequest }
  >,
) => {
  return useMutation({
    mutationFn: ({ id, data }) => updateClub(id, data),
    ...options,
  });
};

export const deleteClub = async (id: string): Promise<void> => {
  return customInstance<void>(getGetClubUrl(id), { method: "DELETE" });
};

export const useDeleteClub = (
  options?: UseMutationOptions<void, unknown, { id: string }>,
) => {
  return useMutation({
    mutationFn: ({ id }) => deleteClub(id),
    ...options,
  });
};
