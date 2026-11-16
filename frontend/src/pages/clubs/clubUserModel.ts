import type { TeamResponse } from "../../api/generated/model";

export type TeamHat = "COACH" | "PLAYER" | "FAN" | "PARENT";

export type UserPlacement = {
  teamId: string;
  teamName: string;
  shortName?: string;
  profileId?: string;
  dateOfBirth?: string;
  hat: TeamHat;
};

/**
 * The account's permission in this club's list. It comes from Keycloak (platform admin) and the
 * club_admins table (this club), not from the primary role: an admin of another club is a plain member here.
 */
export const permissionOf = (user: { platformAdmin?: boolean; clubAdmin?: boolean }): "ADMIN" | "CLUB_ADMIN" | "NORMAL" => {
  if (user.platformAdmin) return "ADMIN";
  if (user.clubAdmin) return "CLUB_ADMIN";
  return "NORMAL";
};

export const placementsForUser = (userId: string, teams: TeamResponse[]): UserPlacement[] => {
  const rows: UserPlacement[] = [];
  for (const team of teams) {
    if (!team.id) continue;
    const base = {
      teamId: team.id,
      teamName: team.name ?? "",
      shortName: team.shortName,
    };
    for (const player of team.players ?? []) {
      if (player.linkedUserId !== userId || !player.id) continue;
      rows.push({
        ...base,
        hat: "PLAYER",
        profileId: player.id,
        dateOfBirth: player.dateOfBirth,
      });
    }
    for (const coach of team.coaches ?? []) {
      if (coach.id !== userId) continue;
      rows.push({ ...base, hat: "COACH", dateOfBirth: coach.dateOfBirth });
    }
    for (const fan of team.fans ?? []) {
      if (fan.id !== userId) continue;
      rows.push({ ...base, hat: "FAN", dateOfBirth: fan.dateOfBirth });
    }
    for (const parent of team.parents ?? []) {
      if (parent.id !== userId) continue;
      rows.push({ ...base, hat: "PARENT", dateOfBirth: parent.dateOfBirth });
    }
  }
  return rows.sort((left, right) => left.teamName.localeCompare(right.teamName));
};

export const birthOf = (placements: UserPlacement[]): string | undefined =>
  placements.find((row) => row.dateOfBirth)?.dateOfBirth;
