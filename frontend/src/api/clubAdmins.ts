import { api } from "./axiosInstance";

export type ClubAdminRow = {
  userId: string;
  firstName?: string | null;
  lastName?: string | null;
  email?: string | null;
  createdAt?: string | null;
};

export const createClubAdminInvite = async (clubId: string) => {
  const response = await api.post<{ token: string }>(`/api/clubs/${clubId}/admin-invites`);
  return response.data;
};

export const listClubAdmins = async (clubId: string) => {
  const response = await api.get<ClubAdminRow[]>(`/api/clubs/${clubId}/admins`);
  return response.data;
};

export const removeClubAdmin = async (clubId: string, userId: string) => {
  await api.delete(`/api/clubs/${clubId}/admins/${userId}`);
};

export const createTeamInClub = async (
  clubId: string,
  data: { name: string; shortName?: string; logoUrl?: string },
) => {
  const response = await api.post(`/api/clubs/${clubId}/teams`, data);
  return response.data;
};
