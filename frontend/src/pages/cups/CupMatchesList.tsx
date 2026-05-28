import { useState } from "react";
import {
  Box,
  Stack,
  Typography,
  Chip,
  IconButton,
  Tooltip,
  Divider,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import ScoreboardOutlinedIcon from "@mui/icons-material/ScoreboardOutlined";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import {
  useGetMatches,
  useScheduleMatch,
  useRecordScore,
  getGetMatchesQueryKey,
  getGetStandingsQueryKey,
} from "../../api/generated/cups/cups";
import { type CupTeamResponse, type CupMatchResponse } from "../../api/generated/model";
import { FilledActionButton } from "../../components/ui/FilledActionButton";
import { AddMatchDialog } from "./dialogs/AddMatchDialog";
import { RecordScoreDialog } from "./dialogs/RecordScoreDialog";

interface CupMatchesListProps {
  cupId: string;
  teams: CupTeamResponse[];
}

export const CupMatchesList = ({ cupId, teams }: CupMatchesListProps) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [isAddMatchOpen, setIsAddMatchOpen] = useState(false);
  const [scoreMatch, setScoreMatch] = useState<CupMatchResponse | null>(null);

  const { data: matchesData } = useGetMatches(cupId);
  const matches = matchesData ?? [];

  const scheduleMatchMutation = useScheduleMatch();
  const recordScoreMutation = useRecordScore();

  const handleAddMatch = async (homeTeamId: string, awayTeamId: string, scheduledAt: string) => {
    await scheduleMatchMutation.mutateAsync({ cupId, data: { homeTeamId, awayTeamId, scheduledAt } });
    queryClient.invalidateQueries({ queryKey: getGetMatchesQueryKey(cupId) });
    setIsAddMatchOpen(false);
  };

  const handleRecordScore = async (homeScore: number, awayScore: number) => {
    if (!scoreMatch?.id) return;
    await recordScoreMutation.mutateAsync({
      cupId,
      matchId: scoreMatch.id,
      data: { homeScore, awayScore },
    });
    queryClient.invalidateQueries({ queryKey: getGetMatchesQueryKey(cupId) });
    queryClient.invalidateQueries({ queryKey: getGetStandingsQueryKey(cupId) });
    setScoreMatch(null);
  };

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
        <FilledActionButton startIcon={<AddIcon />} size="small" onClick={() => setIsAddMatchOpen(true)}>
          {t("cups.add-match")}
        </FilledActionButton>
      </Stack>

      {matches.length === 0 ? (
        <Typography variant="body2" sx={{ color: "text.secondary" }}>
          {t("cups.no-matches")}
        </Typography>
      ) : (
        <Stack divider={<Divider />}>
          {matches.map((match) => (
            <Box
              key={match.id}
              sx={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                py: 1,
              }}
            >
              <Box sx={{ display: "flex", alignItems: "center", gap: 1, flex: 1, minWidth: 0 }}>
                <Typography variant="body2" sx={{ minWidth: 80, textAlign: "right", flexShrink: 0 }} noWrap>
                  {match.homeTeam?.name}
                </Typography>
                <Typography
                  variant="body2"
                  fontWeight="bold"
                  sx={{
                    px: 1.5,
                    py: 0.25,
                    bgcolor: match.played ? "action.selected" : "action.hover",
                    borderRadius: 1,
                    minWidth: 52,
                    textAlign: "center",
                    flexShrink: 0,
                  }}
                >
                  {match.played ? `${match.homeScore ?? 0} - ${match.awayScore ?? 0}` : t("common.vs")}
                </Typography>
                <Typography variant="body2" sx={{ minWidth: 80, flexShrink: 0 }} noWrap>
                  {match.awayTeam?.name}
                </Typography>
              </Box>
              <Box sx={{ display: "flex", alignItems: "center", gap: 1, flexShrink: 0 }}>
                {match.scheduledAt && (
                  <Chip
                    label={new Date(match.scheduledAt).toLocaleString(undefined, { dateStyle: "short", timeStyle: "short" })}
                    size="small"
                    variant="outlined"
                    sx={{ fontSize: "0.7rem" }}
                  />
                )}
                <Tooltip title={t("cups.record-score")}>
                  <IconButton size="small" onClick={() => setScoreMatch(match)}>
                    <ScoreboardOutlinedIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              </Box>
            </Box>
          ))}
        </Stack>
      )}

      <AddMatchDialog
        open={isAddMatchOpen}
        onClose={() => setIsAddMatchOpen(false)}
        onAdd={handleAddMatch}
        teams={teams}
      />

      <RecordScoreDialog
        open={scoreMatch !== null}
        onClose={() => setScoreMatch(null)}
        onSave={handleRecordScore}
        match={scoreMatch}
      />
    </Box>
  );
};
