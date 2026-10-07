import { useChildren } from "../context/ChildrenContext";
import { useView } from "../context/ViewContext";
import { ROLES } from "../types/roles";

export const useChildScope = () => {
  const { activeView } = useView();
  const { selected, isLoading } = useChildren();
  const active = activeView === ROLES.PARENT;
  const selectedTeamIds = active && selected
    ? new Set((selected.teams ?? []).map((team) => team.teamId))
    : new Set<string>();

  const scopeTeams = <T extends { id?: string | null }>(teams: T[]): T[] => {
    if (!active) return teams;
    if (isLoading || selectedTeamIds.size === 0) return [];
    return teams.filter((team) => team.id != null && selectedTeamIds.has(team.id));
  };

  return { active, selectedTeamIds, isLoading: active && isLoading, scopeTeams };
};
