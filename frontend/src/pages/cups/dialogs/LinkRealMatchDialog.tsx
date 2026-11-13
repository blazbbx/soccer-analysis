import { useEffect, useMemo, useState } from "react";
import {
  Alert,
  Box,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  List,
  ListItemButton,
  ListItemText,
  Radio,
  Typography,
} from "@mui/material";
import { useTranslation } from "react-i18next";
import { useLinkRealMatch, useUnlinkRealMatch } from "../../../api/generated/cups/cups";
import { type CupMatchResponse } from "../../../api/generated/model";
import { usePagedMatches } from "../../../api/pagedLists";
import { PICKER_PAGE_SIZE } from "../../../api/paging";
import { invalidateCups } from "../../../api/cups";
import { useView } from "../../../context/ViewContext";
import { PrimaryButton } from "../../../components/ui/PrimaryButton";
import { SecondaryButton } from "../../../components/ui/SecondaryButton";
import { useQueryClient } from "@tanstack/react-query";
import { useDialogSubmit } from "./useDialogSubmit";

interface LinkRealMatchDialogProps {
  open: boolean;
  onClose: () => void;
  cupId: string;
  match: CupMatchResponse | null;
}

/**
 * 2.1: pick the analysed match (video) of a cup match. The list holds the matches of the cup match's two
 * real teams, the one closest in time to the cup match first.
 */
export const LinkRealMatchDialog = ({ open, onClose, cupId, match }: LinkRealMatchDialogProps) => {
  const { t } = useTranslation();
  const { activeView } = useView();
  const queryClient = useQueryClient();
  const { saving, error, run, clearError } = useDialogSubmit();
  const [selectedId, setSelectedId] = useState<string | null>(null);

  useEffect(() => {
    if (open) setSelectedId(match?.realMatchId ?? null);
  }, [open, match]);

  const teamIds = [match?.homeTeam?.realTeam?.id, match?.awayTeam?.realTeam?.id]
    .filter((id): id is string => Boolean(id));
  const { data, isLoading } = usePagedMatches({
    page: 1,
    size: PICKER_PAGE_SIZE,
    view: activeView?.toUpperCase(),
    teamIds,
    enabled: open && teamIds.length > 0,
  });

  const candidates = useMemo(() => {
    const target = match?.scheduledAt ? new Date(match.scheduledAt).getTime() : Date.now();
    const distance = (date?: string) => (date ? Math.abs(new Date(date).getTime() - target) : Number.MAX_SAFE_INTEGER);
    return [...(data?.items ?? [])].sort((a, b) => distance(a.matchDate) - distance(b.matchDate));
  }, [data, match?.scheduledAt]);

  const linkMutation = useLinkRealMatch();
  const unlinkMutation = useUnlinkRealMatch();

  const handleClose = () => {
    if (saving) return;
    clearError();
    onClose();
  };

  const afterSave = async (ok: boolean) => {
    if (!ok) return;
    await invalidateCups(queryClient);
    onClose();
  };

  const handleSave = async () => {
    if (!match?.id || !selectedId) return;
    const matchId = match.id;
    await afterSave(await run(() => linkMutation.mutateAsync({ cupId, matchId, params: { realMatchId: selectedId } })));
  };

  const handleUnlink = async () => {
    if (!match?.id) return;
    const matchId = match.id;
    await afterSave(await run(() => unlinkMutation.mutateAsync({ cupId, matchId })));
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontWeight: "bold" }}>{t("cups.link-video")}</DialogTitle>
      <DialogContent dividers>
        <Typography variant="body2" sx={{ color: "text.secondary", mb: 2 }}>
          {match?.homeTeam?.name} vs {match?.awayTeam?.name}
        </Typography>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        {isLoading ? (
          <Box sx={{ display: "flex", justifyContent: "center", py: 3 }}>
            <CircularProgress size={24} />
          </Box>
        ) : candidates.length === 0 ? (
          <Typography variant="body2" sx={{ color: "text.secondary" }}>
            {t("cups.no-videos")}
          </Typography>
        ) : (
          <List dense disablePadding>
            {candidates.map((candidate) => (
              <ListItemButton
                key={candidate.id}
                selected={candidate.id === selectedId}
                onClick={() => setSelectedId(candidate.id ?? null)}
              >
                <Radio checked={candidate.id === selectedId} size="small" sx={{ mr: 1 }} />
                <ListItemText
                  primary={`${candidate.homeTeamName ?? "?"} – ${candidate.awayTeamName ?? "?"}${
                    candidate.homeScore != null && candidate.awayScore != null
                      ? `  ${candidate.homeScore}–${candidate.awayScore}`
                      : ""
                  }`}
                  secondary={candidate.matchDate
                    ? new Date(candidate.matchDate).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })
                    : undefined}
                />
              </ListItemButton>
            ))}
          </List>
        )}
      </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        {match?.realMatchId && (
          <SecondaryButton onClick={handleUnlink} disabled={saving} sx={{ mr: "auto" }}>
            {t("cups.unlink-video")}
          </SecondaryButton>
        )}
        <SecondaryButton onClick={handleClose} disabled={saving}>{t("common.cancel")}</SecondaryButton>
        <PrimaryButton
          onClick={handleSave}
          variant="contained"
          loading={saving}
          disabled={!selectedId || selectedId === match?.realMatchId}
        >
          {t("common.save")}
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
