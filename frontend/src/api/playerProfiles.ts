import { customInstance } from "./axiosInstance";

export interface CreatePlayerProfileRequest {
  firstName: string;
  lastName: string;
  dateOfBirth?: string;
}

export const createPlayerProfile = (teamId: string, data: CreatePlayerProfileRequest) =>
  customInstance<void>(`/api/teams/${teamId}/player-profiles`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data),
  });
