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

export const hatForRole = (role?: string): TeamHat | null => {
  switch (role) {
    case "COACH":
    case "PLAYER":
    case "FAN":
    case "PARENT":
      return role;
    default:
      return null;
  }
};

export const permissionForRole = (role?: string): "ADMIN" | "CLUB_ADMIN" | "NORMAL" => {
  if (role === "ADMIN") return "ADMIN";
  if (role === "CLUB_ADMIN") return "CLUB_ADMIN";
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
