import type { TeamResponse } from "../../api/generated/model";
import { TeamAutocomplete, type TeamPick } from "../../components/TeamAutocomplete";
import { TeamSelectorDropdown } from "../chat/TeamSelectorDropdown";
import { useTranslation } from "react-i18next";

export const TravelTeamBar = ({
  teams,
  selectedTeamId,
  onSelect,
  searchTeams,
  selectedTeam,
}: {
  teams: TeamResponse[];
  selectedTeamId: string;
  onSelect: (teamId: string, team?: TeamPick | null) => void;
  searchTeams: boolean;
  selectedTeam: TeamPick | null;
}) => {
  const { t } = useTranslation();

  if (searchTeams && teams.length !== 1) {
    return (
      <TeamAutocomplete
        teams={teams}
        valueId={selectedTeamId || null}
        onChange={(teamId, team) => onSelect(teamId ?? "", team)}
        selectedLabel={selectedTeam?.name ?? undefined}
        label={t("travel.select-team")}
        size="small"
      />
    );
  }

  return (
    <TeamSelectorDropdown
      teams={teams}
      selectedTeamId={selectedTeamId}
      onSelect={(teamId) => onSelect(teamId)}
    />
  );
};
