import { useState } from "react";
import { Alert, Box, CircularProgress, Pagination, Stack, TextField, Typography } from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import { useTranslation } from "react-i18next";
import { useListClubTeams } from "../../../api/generated/clubs/clubs";
import { type TeamResponse } from "../../../api/generated/model";
import { PrimaryButton } from "../../../components/ui/PrimaryButton";
import { STAT_COLORS } from "../../../constants/colors";
import { matchesQuery, monogram, pageOf } from "../paging";
import { ClubTeamDialog } from "../dialogs/ClubTeamDialog";

type ClubTeamCardModel = {
  id: string;
  name?: string;
  shortName?: string;
  logoUrl?: string;
  playerCount: number;
};

const toCard = (team: TeamResponse): ClubTeamCardModel => ({
  id: team.id ?? "",
  name: team.name,
  shortName: team.shortName,
  logoUrl: team.logoUrl,
  playerCount: team.players?.length ?? 0,
});

const TeamCard = ({
  team,
  onOpen,
}: {
  team: ClubTeamCardModel;
  onOpen?: (teamId: string) => void;
}) => {
  const { t } = useTranslation();
  const initials = monogram(team.name, team.shortName);
  return (
    <Box
      role={onOpen ? "button" : undefined}
      tabIndex={onOpen ? 0 : undefined}
      onClick={() => onOpen?.(team.id)}
      onKeyDown={(event) => {
        if (!onOpen) return;
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          onOpen(team.id);
        }
      }}
      sx={{
        backgroundColor: "background.paper",
        borderRadius: 3,
        p: 2,
        border: "1px solid",
        borderColor: "divider",
        cursor: onOpen ? "pointer" : "default",
      }}
    >
      <Stack direction="row" spacing={2} alignItems="center">
        <Box
          sx={{
            width: 48,
            height: 48,
            backgroundColor: STAT_COLORS.blueAccent,
            borderRadius: 2,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            color: "white",
            fontWeight: "bold",
            overflow: "hidden",
            flexShrink: 0,
          }}
        >
          {team.logoUrl ? (
            <Box
              component="img"
              src={team.logoUrl}
              alt={team.name}
              sx={{ width: "100%", height: "100%", objectFit: "cover" }}
            />
          ) : (
            initials
          )}
        </Box>
        <Box>
          <Typography sx={{ color: "text.primary", fontWeight: 600 }}>{team.name}</Typography>
          <Typography variant="body2" sx={{ color: "text.secondary" }}>
            {team.shortName?.trim()
              ? `${team.shortName} · ${t("clubs.player-count", { count: team.playerCount })}`
              : t("clubs.player-count", { count: team.playerCount })}
          </Typography>
        </Box>
        {onOpen && <ChevronRightIcon sx={{ color: "text.secondary", ml: "auto" }} />}
      </Stack>
    </Box>
  );
};

export const ClubTeamsTab = ({
  clubId,
  clubName,
  clubShortName,
  canManage,
  query,
  page,
  onQueryChange,
  onPageChange,
  onAddTeam,
}: {
  clubId: string;
  clubName?: string;
  clubShortName?: string;
  canManage: boolean;
  query: string;
  page: number;
  onQueryChange: (value: string) => void;
  onPageChange: (page: number) => void;
  onAddTeam: () => void;
}) => {
  const { t } = useTranslation();
  const [openTeamId, setOpenTeamId] = useState<string | null>(null);
  const { data: teams = [], isLoading, isError } = useListClubTeams(clubId);
  const cards = teams.map(toCard);
  const filtered = cards.filter((team) => matchesQuery(query, [team.name, team.shortName]));
  const paging = pageOf(filtered, page);

  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} alignItems={{ sm: "center" }}>
        <TextField
          fullWidth
          size="small"
          label={t("clubs.search")}
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
        />
        {canManage && (
          <PrimaryButton startIcon={<AddIcon />} onClick={onAddTeam} sx={{ flexShrink: 0 }}>
            {t("clubs.add-team")}
          </PrimaryButton>
        )}
      </Stack>
      {canManage && (
        <Typography variant="body2" sx={{ color: "text.secondary" }}>
          {t("clubs.team-click")}
        </Typography>
      )}
      {isLoading ? (
        <Box sx={{ display: "flex", justifyContent: "center", py: 3 }}>
          <CircularProgress size={28} />
        </Box>
      ) : isError ? (
        <Alert severity="error">{t("clubs.teams-load-error")}</Alert>
      ) : teams.length === 0 ? (
        <Typography variant="body2" sx={{ color: "text.secondary" }}>
          {t("clubs.no-teams")}
        </Typography>
      ) : filtered.length === 0 ? (
        <Typography variant="body2" sx={{ color: "text.secondary" }}>
          {t("clubs.search-empty-teams")}
        </Typography>
      ) : (
        <>
          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: { xs: "1fr", sm: "1fr 1fr" },
              gap: 2,
            }}
          >
            {paging.visible.map((team) => (
              <TeamCard
                key={team.id}
                team={team}
                onOpen={canManage ? setOpenTeamId : undefined}
              />
            ))}
          </Box>
          {paging.totalPages > 1 && (
            <Pagination
              count={paging.totalPages}
              page={paging.safePage}
              onChange={(_event, next) => onPageChange(next)}
              siblingCount={2}
              boundaryCount={0}
              sx={{ alignSelf: "center" }}
            />
          )}
        </>
      )}
      <ClubTeamDialog
        open={Boolean(openTeamId)}
        clubId={clubId}
        clubName={clubName}
        clubShortName={clubShortName}
        team={teams.find((team) => team.id === openTeamId) ?? null}
        clubTeams={teams}
        onClose={() => setOpenTeamId(null)}
      />
    </Stack>
  );
};
