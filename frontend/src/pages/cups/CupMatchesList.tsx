import { useState } from "react";
import { useNavigate } from "react-router-dom";
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
import PlayCircleOutlinedIcon from "@mui/icons-material/PlayCircleOutlined";
import VideoLibraryOutlinedIcon from "@mui/icons-material/VideoLibraryOutlined";
import RestartAltIcon from "@mui/icons-material/RestartAlt";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import {
  useGetMatches,
  useScheduleMatch,
  useRecordScore,
} from "../../api/generated/cups/cups";
import { type CupTeamResponse, type CupMatchResponse } from "../../api/generated/model";
import { invalidateCups, useClearCupScore, useCupViewParams } from "../../api/cups";
import { FilledActionButton } from "../../components/ui/FilledActionButton";
import { AddMatchDialog } from "./dialogs/AddMatchDialog";
import { RecordScoreDialog } from "./dialogs/RecordScoreDialog";
import { LinkRealMatchDialog } from "./dialogs/LinkRealMatchDialog";
import { CupConfirmDialog } from "./dialogs/CupConfirmDialog";
import { ownRowSx } from "./ownTeamHighlight";

interface CupMatchesListProps {
  cupId: string;
  teams: CupTeamResponse[];
  /** From the backend (1.4): the caller may edit this cup. Every edit button depends on it. */
  canManage: boolean;
}

export const CupMatchesList = ({ cupId, teams, canManage }: CupMatchesListProps) => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const viewParams = useCupViewParams();
  const [isAddMatchOpen, setIsAddMatchOpen] = useState(false);
  const [scoreMatch, setScoreMatch] = useState<CupMatchResponse | null>(null);
  const [videoMatch, setVideoMatch] = useState<CupMatchResponse | null>(null);
  const [clearMatch, setClearMatch] = useState<CupMatchResponse | null>(null);

  // The backend sends the matches in time order (CU9); the list keeps that order.
  const { data: matchesData } = useGetMatches(cupId, viewParams);
  const matches = matchesData ?? [];

  const scheduleMatchMutation = useScheduleMatch();
  const recordScoreMutation = useRecordScore();
  const clearScoreMutation = useClearCupScore();

  const handleAddMatch = async (homeTeamId: string, awayTeamId: string, scheduledAt: string) => {
    await scheduleMatchMutation.mutateAsync({ cupId, data: { homeTeamId, awayTeamId, scheduledAt } });
    await invalidateCups(queryClient);
    setIsAddMatchOpen(false);
  };

  const handleRecordScore = async (homeScore: number, awayScore: number) => {
    if (!scoreMatch?.id) return;
    await recordScoreMutation.mutateAsync({ cupId, matchId: scoreMatch.id, data: { homeScore, awayScore } });
    await invalidateCups(queryClient);
    setScoreMatch(null);
  };

  const handleClearScore = async () => {
    if (!clearMatch?.id) return;
    await clearScoreMutation.mutateAsync({ cupId, matchId: clearMatch.id });
  };

  const hasRealTeam = (match: CupMatchResponse) => Boolean(match.homeTeam?.realTeam?.id || match.awayTeam?.realTeam?.id);
  const isOwnMatch = (match: CupMatchResponse) => Boolean(match.homeTeam?.ownTeam || match.awayTeam?.ownTeam);

  return (
    <Box>
      {canManage && (
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
          <FilledActionButton startIcon={<AddIcon />} size="small" onClick={() => setIsAddMatchOpen(true)}>
            {t("cups.add-match")}
          </FilledActionButton>
        </Stack>
      )}

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
                flexWrap: "wrap",
                gap: 1,
                py: 1,
                px: 1,
                ...(isOwnMatch(match) ? ownRowSx : {}),
              }}
            >
              <Box sx={{ display: "flex", alignItems: "center", gap: 1, flex: 1, minWidth: 0 }}>
                <Typography
                  variant="body2"
                  sx={{ minWidth: 80, textAlign: "right", flexShrink: 0, fontWeight: match.homeTeam?.ownTeam ? "bold" : undefined }}
                  noWrap
                >
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
                <Typography
                  variant="body2"
                  sx={{ minWidth: 80, flexShrink: 0, fontWeight: match.awayTeam?.ownTeam ? "bold" : undefined }}
                  noWrap
                >
                  {match.awayTeam?.name}
                </Typography>
              </Box>
              <Box sx={{ display: "flex", alignItems: "center", gap: 0.5, flexShrink: 0 }}>
                {match.scheduledAt && (
                  <Chip
                    label={new Date(match.scheduledAt).toLocaleString(undefined, { dateStyle: "short", timeStyle: "short" })}
                    size="small"
                    variant="outlined"
                    sx={{ fontSize: "0.7rem" }}
                  />
                )}
                {match.realMatchId && (
                  <Tooltip title={t("cups.open-match")}>
                    <IconButton size="small" onClick={() => navigate(`/matches/${match.realMatchId}`)}>
                      <PlayCircleOutlinedIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
                {canManage && (
                  <Tooltip title={hasRealTeam(match) ? t("cups.link-video") : t("cups.link-video-needs-team")}>
                    {/* The span keeps the tooltip working on a disabled button. */}
                    <span>
                      <IconButton size="small" disabled={!hasRealTeam(match)} onClick={() => setVideoMatch(match)}>
                        <VideoLibraryOutlinedIcon fontSize="small" />
                      </IconButton>
                    </span>
                  </Tooltip>
                )}
                {canManage && (
                  <Tooltip title={t("cups.record-score")}>
                    <IconButton size="small" onClick={() => setScoreMatch(match)}>
                      <ScoreboardOutlinedIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
                {canManage && match.played && (
                  <Tooltip title={t("cups.clear-score")}>
                    <IconButton size="small" onClick={() => setClearMatch(match)}>
                      <RestartAltIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )}
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
      <LinkRealMatchDialog
        open={videoMatch !== null}
        onClose={() => setVideoMatch(null)}
        cupId={cupId}
        match={videoMatch}
      />
      <CupConfirmDialog
        open={clearMatch !== null}
        title={t("cups.clear-score")}
        description={t("cups.clear-score-confirm", {
          match: `${clearMatch?.homeTeam?.name ?? ""} – ${clearMatch?.awayTeam?.name ?? ""}`,
        })}
        confirmLabel={t("cups.clear-score")}
        onConfirm={handleClearScore}
        onClose={() => setClearMatch(null)}
      />
    </Box>
  );
};
