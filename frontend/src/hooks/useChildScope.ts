import { useChildren } from "../context/ChildrenContext";
import { useView } from "../context/ViewContext";
import { ROLES } from "../types/roles";
import type { TeamResponse } from "../api/generated/model";
import type { MatchResponse } from "../api/generated/model/matchResponse";
import type { CupResponse } from "../api/generated/model/cupResponse";

export const useChildScope = () => {
  const { activeView } = useView();
  const { selected } = useChildren();
  const active = activeView === ROLES.PARENT && Boolean(selected);
  const selectedTeamIds = active
    ? new Set((selected?.teams ?? []).map((team) => team.teamId))
    : new Set<string>();

  const scopeTeams = (teams: TeamResponse[]) => {
    if (!active) return teams;
    if (selectedTeamIds.size === 0) return [];
    return teams.filter((team) => team.id != null && selectedTeamIds.has(team.id));
  };

  const scopeMatches = (matches: MatchResponse[]) => {
    if (!active) return matches;
    return matches.filter(
      (match) =>
        (match.homeTeamId == null && match.awayTeamId == null)
        || (match.homeTeamId != null && selectedTeamIds.has(match.homeTeamId))
        || (match.awayTeamId != null && selectedTeamIds.has(match.awayTeamId)),
    );
  };

  const scopeCups = (cups: CupResponse[]) => {
    if (!active) return cups;
    if (selectedTeamIds.size === 0) return [];
    return cups.filter((cup) =>
      cup.teams?.some((team) => team.realTeam?.id != null && selectedTeamIds.has(team.realTeam.id)),
    );
  };

  return { active, selectedTeamIds, scopeTeams, scopeMatches, scopeCups };
};
