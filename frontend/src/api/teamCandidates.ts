import { customInstance } from "./axiosInstance";

export interface TeamCandidate {
  id?: string;
  email?: string;
  firstName?: string;
  lastName?: string;
  /** Platform admin in Keycloak (user lists only). */
  platformAdmin?: boolean;
  /** Admin of the club whose list this is (club user list only). */
  clubAdmin?: boolean;
}

export const updateClubUser = (
  userId: string,
  body: { firstName: string; lastName: string; dateOfBirth?: string },
) =>
  customInstance<TeamCandidate>(`/api/users/${userId}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

export const removeParentFromTeam = (teamId: string, parentId: string) =>
  customInstance<void>(`/api/teams/${teamId}/parents/${parentId}`, { method: "DELETE" });
