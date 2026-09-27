import { customInstance } from "./axiosInstance";
import type { InviteTokenResponse } from "./generated/model";

export interface ChildTeam {
  teamId: string;
  teamName?: string;
  clubId?: string;
  clubName?: string;
}

export interface ChildResponse {
  profileId: string;
  firstName: string;
  lastName: string;
  linkedUserId?: string;
  teams?: ChildTeam[];
}

export const formatChildTeam = (team: ChildTeam) => {
  const club = team.clubName?.trim();
  const name = team.teamName?.trim();
  if (club && name && club !== name) return `${club} · ${name}`;
  return name || club || "";
};

export const childClubAndTeamLabel = (child: ChildResponse) =>
  (child.teams ?? []).map(formatChildTeam).filter(Boolean).join(", ");

export interface TeamInviteDetails {
  invitedRole?: string;
  inviteKind?: string;
  childFirstName?: string;
  childLastName?: string;
  teamName?: string;
}

export const listMyChildren = () =>
  customInstance<ChildResponse[]>("/api/me/children", { method: "GET" });

export const getInviteDetails = (token: string) =>
  customInstance<TeamInviteDetails>(`/api/team-invites/${token}`, { method: "GET" });

export const createParentInvite = (profileId: string) =>
  customInstance<InviteTokenResponse>(
    `/api/player-profiles/${profileId}/parent-invites`,
    { method: "POST" },
  );

export const createOpenParentInvite = (teamId: string) =>
  customInstance<InviteTokenResponse>(
    `/api/team-invites/${teamId}/invites?role=PARENT`,
    { method: "POST" },
  );

export const acceptInviteWithDetails = (
  token: string,
  body: {
    childFirstName?: string;
    childLastName?: string;
    childDateOfBirth?: string;
    dateOfBirth?: string;
    existingProfileId?: string;
  },
) =>
  customInstance<void>(`/api/team-invites/${token}/accept`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

export const createChildLoginInvite = (profileId: string) =>
  customInstance<InviteTokenResponse>(
    `/api/player-profiles/${profileId}/child-login-invites`,
    { method: "POST" },
  );

export interface CreateChildLoginRequest {
  email: string;
  password: string;
  firstName?: string;
  lastName?: string;
}

export const createChildLogin = (profileId: string, body: CreateChildLoginRequest) =>
  customInstance<void>(`/api/player-profiles/${profileId}/child-login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
