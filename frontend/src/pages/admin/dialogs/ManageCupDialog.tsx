import { useState } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Box,
  Table,
  TableHead,
  TableRow,
  TableCell,
  TableBody,
  IconButton,
  Typography,
  Chip,
  Divider,
  Stack,
  CircularProgress,
  Tooltip,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import ScoreboardOutlinedIcon from "@mui/icons-material/ScoreboardOutlined";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import {
  useGetTeams,
  useAddTeamToCup,
  useUpdateTeam1,
  useDeleteTeam1,
  useGetMatches,
  useScheduleMatch,
  useUpdateMatch1,
  useDeleteMatch1,
  useRecordScore,
} from "../../../api/generated/cups/cups";
import { invalidateCups, useCupViewParams } from "../../../api/cups";
import { type CupTeamResponse, type CupMatchResponse, type TeamResponse } from "../../../api/generated/model";
import { SecondaryButton } from "../../../components/ui/SecondaryButton";
import { FilledActionButton } from "../../../components/ui/FilledActionButton";
import { AddTeamDialog } from "../../cups/dialogs/AddTeamDialog";
import { AddMatchDialog } from "../../cups/dialogs/AddMatchDialog";
import { RecordScoreDialog } from "../../cups/dialogs/RecordScoreDialog";
import { EditCupTeamDialog } from "./EditCupTeamDialog";
import { EditCupMatchDialog } from "./EditCupMatchDialog";
import { CupConfirmDialog } from "../../cups/dialogs/CupConfirmDialog";

interface ManageCupDialogProps {
  open: boolean;
  onClose: () => void;
  cup: { id?: string; name?: string | null } | null;
  myTeams: TeamResponse[];
}

export const ManageCupDialog = ({ open, onClose, cup, myTeams }: ManageCupDialogProps) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const cupId = cup?.id ?? "";

  const [addTeamOpen, setAddTeamOpen] = useState(false);
  const [editTeam, setEditTeam] = useState<CupTeamResponse | null>(null);
  const [deleteTeam, setDeleteTeam] = useState<CupTeamResponse | null>(null);

  const [addMatchOpen, setAddMatchOpen] = useState(false);
  const [editMatch, setEditMatch] = useState<CupMatchResponse | null>(null);
  const [scoreMatch, setScoreMatch] = useState<CupMatchResponse | null>(null);
  const [deleteMatch, setDeleteMatch] = useState<CupMatchResponse | null>(null);

  const viewParams = useCupViewParams();
  const { data: teamsData, isLoading: isLoadingTeams } = useGetTeams(cupId, viewParams, { query: { enabled: open && !!cupId } });
  const { data: matchesData, isLoading: isLoadingMatches } = useGetMatches(cupId, viewParams, { query: { enabled: open && !!cupId } });

  const teams = teamsData ?? [];
  const matches = matchesData ?? [];

  // Every save is awaited: on failure the sub-dialog stays open and shows the backend's message (1.6).
  const refresh = () => invalidateCups(queryClient);
  const { mutateAsync: addTeam } = useAddTeamToCup();
  const { mutateAsync: updateTeam } = useUpdateTeam1();
  const { mutateAsync: deleteTeamMutate } = useDeleteTeam1();
  const { mutateAsync: scheduleMatch } = useScheduleMatch();
  const { mutateAsync: updateMatch } = useUpdateMatch1();
  const { mutateAsync: deleteMatchMutate } = useDeleteMatch1();
  const { mutateAsync: recordScore } = useRecordScore();

  const handleAddTeam = async (name: string, realTeamId?: string) => {
    await addTeam({ cupId, data: { name, realTeamId } });
    await refresh();
    setAddTeamOpen(false);
  };

  const handleEditTeam = async (name: string, realTeamId?: string) => {
    if (!editTeam?.id) return;
    await updateTeam({ cupId, teamId: editTeam.id, data: { name, realTeamId } });
    await refresh();
    setEditTeam(null);
  };

  const handleDeleteTeam = async () => {
    if (!deleteTeam?.id) return;
    await deleteTeamMutate({ cupId, teamId: deleteTeam.id });
    await refresh();
  };

  const handleAddMatch = async (homeTeamId: string, awayTeamId: string, scheduledAt: string) => {
    await scheduleMatch({ cupId, data: { homeTeamId, awayTeamId, scheduledAt } });
    await refresh();
    setAddMatchOpen(false);
  };

  const handleEditMatch = async (homeTeamId: string, awayTeamId: string, scheduledAt: string) => {
    if (!editMatch?.id) return;
    await updateMatch({ cupId, matchId: editMatch.id, data: { homeTeamId, awayTeamId, scheduledAt } });
    await refresh();
    setEditMatch(null);
  };

  const handleDeleteMatch = async () => {
    if (!deleteMatch?.id) return;
    await deleteMatchMutate({ cupId, matchId: deleteMatch.id });
    await refresh();
  };

  const handleRecordScore = async (homeScore: number, awayScore: number) => {
    if (!scoreMatch?.id) return;
    await recordScore({ cupId, matchId: scoreMatch.id, data: { homeScore, awayScore } });
    await refresh();
    setScoreMatch(null);
  };

  return (
    <>
      <Dialog open={open} onClose={onClose} fullWidth maxWidth="lg">
        <DialogTitle sx={{ fontWeight: "bold" }}>
          {t("admin.manage-cup")}: {cup?.name}
        </DialogTitle>
        <DialogContent dividers>
          {/* Teams section */}
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
            <Typography variant="subtitle1" fontWeight="bold">
              {t("admin.cup-teams")}
            </Typography>
            <FilledActionButton startIcon={<AddIcon />} size="small" onClick={() => setAddTeamOpen(true)}>
              {t("cups.add-team")}
            </FilledActionButton>
          </Stack>

          {isLoadingTeams ? (
            <Box sx={{ display: "flex", justifyContent: "center", py: 3 }}>
              <CircularProgress size={24} />
            </Box>
          ) : teams.length === 0 ? (
            <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
              {t("cups.no-standings")}
            </Typography>
          ) : (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>{t("cups.team-name")}</TableCell>
                  <TableCell sx={{ fontWeight: "bold" }}>{t("admin.linked-team")}</TableCell>
                  <TableCell align="right" />
                </TableRow>
              </TableHead>
              <TableBody>
                {teams.map((team) => (
                  <TableRow key={team.id} hover>
                    <TableCell>{team.name}</TableCell>
                    <TableCell>
                      {team.realTeam?.name ? (
                        <Chip label={team.realTeam.name} size="small" variant="outlined" />
                      ) : (
                        <Typography variant="caption" color="text.secondary">—</Typography>
                      )}
                    </TableCell>
                    <TableCell align="right">
                      <Tooltip title={t("common.edit")}>
                        <IconButton size="small" onClick={() => setEditTeam(team)}>
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title={t("common.delete")}>
                        <IconButton size="small" color="error" onClick={() => setDeleteTeam(team)}>
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}

          <Divider sx={{ my: 3 }} />

          {/* Matches section */}
          <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
            <Typography variant="subtitle1" fontWeight="bold">
              {t("cups.matches")}
            </Typography>
            <FilledActionButton startIcon={<AddIcon />} size="small" onClick={() => setAddMatchOpen(true)}>
              {t("cups.add-match")}
            </FilledActionButton>
          </Stack>

          {isLoadingMatches ? (
            <Box sx={{ display: "flex", justifyContent: "center", py: 3 }}>
              <CircularProgress size={24} />
            </Box>
          ) : matches.length === 0 ? (
            <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
              {t("cups.no-matches")}
            </Typography>
          ) : (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ fontWeight: "bold" }}>{t("cups.home-team")}</TableCell>
                  <TableCell align="center" sx={{ fontWeight: "bold" }}>{t("admin.score")}</TableCell>
                  <TableCell sx={{ fontWeight: "bold" }}>{t("cups.away-team")}</TableCell>
                  <TableCell sx={{ fontWeight: "bold" }}>{t("cups.match-date")}</TableCell>
                  <TableCell align="right" />
                </TableRow>
              </TableHead>
              <TableBody>
                {matches.map((match) => (
                  <TableRow key={match.id} hover>
                    <TableCell>{match.homeTeam?.name ?? "—"}</TableCell>
                    <TableCell align="center">
                      <Typography variant="body2" fontWeight={match.played ? "bold" : "normal"}>
                        {match.played
                          ? `${match.homeScore ?? 0} – ${match.awayScore ?? 0}`
                          : t("common.vs")}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      {match.awayTeam?.name ?? "—"}
                    </TableCell>
                    <TableCell>
                      {match.scheduledAt
                        ? new Date(match.scheduledAt).toLocaleString()
                        : "—"}
                    </TableCell>
                    <TableCell align="right">
                      <Tooltip title={t("common.edit")}>
                        <IconButton size="small" onClick={() => setEditMatch(match)}>
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title={t("cups.record-score")}>
                        <IconButton size="small" onClick={() => setScoreMatch(match)}>
                          <ScoreboardOutlinedIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title={t("common.delete")}>
                        <IconButton size="small" color="error" onClick={() => setDeleteMatch(match)}>
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </DialogContent>
        <DialogActions sx={{ p: 2 }}>
          <SecondaryButton onClick={onClose}>{t("common.cancel")}</SecondaryButton>
        </DialogActions>
      </Dialog>

      {/* Sub-dialogs rendered outside the main dialog to avoid nesting issues */}
      <AddTeamDialog
        open={addTeamOpen}
        onClose={() => setAddTeamOpen(false)}
        onAdd={handleAddTeam}
        myTeams={myTeams}
      />
      <EditCupTeamDialog
        open={!!editTeam}
        onClose={() => setEditTeam(null)}
        onSave={handleEditTeam}
        team={editTeam}
        myTeams={myTeams}
      />
      <CupConfirmDialog
        open={!!deleteTeam}
        title={t("admin.delete-cup-team")}
        description={t("admin.delete-cup-team-confirm")}
        confirmLabel={t("common.delete")}
        onConfirm={handleDeleteTeam}
        onClose={() => setDeleteTeam(null)}
      />
      <AddMatchDialog
        open={addMatchOpen}
        onClose={() => setAddMatchOpen(false)}
        onAdd={handleAddMatch}
        teams={teams}
      />
      <EditCupMatchDialog
        open={!!editMatch}
        onClose={() => setEditMatch(null)}
        onSave={handleEditMatch}
        match={editMatch}
        teams={teams}
      />
      <RecordScoreDialog
        open={!!scoreMatch}
        onClose={() => setScoreMatch(null)}
        onSave={handleRecordScore}
        match={scoreMatch}
      />
      <CupConfirmDialog
        open={!!deleteMatch}
        title={t("admin.delete-cup-match")}
        description={t("admin.delete-cup-match-confirm")}
        confirmLabel={t("common.delete")}
        onConfirm={handleDeleteMatch}
        onClose={() => setDeleteMatch(null)}
      />
    </>
  );
};
