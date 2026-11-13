import { useState } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Stack,
  TextField,
  Typography,
  Box,
  Alert,
  Button,
} from "@mui/material";
import { useTranslation } from "react-i18next";
import { PrimaryButton } from "../../../components/ui/PrimaryButton";
import { SecondaryButton } from "../../../components/ui/SecondaryButton";
import { type CupMatchResponse, type MatchResponse } from "../../../api/generated/model";
import { useGetMatch } from "../../../api/generated/match-controller/match-controller";
import { scoreFromAnalysedMatch } from "../../../utils/cupScore";
import { useDialogSubmit } from "./useDialogSubmit";

interface RecordScoreDialogProps {
  open: boolean;
  onClose: () => void;
  onSave: (homeScore: number, awayScore: number) => Promise<unknown>;
  match: CupMatchResponse | null;
}

export const RecordScoreDialog = ({ open, onClose, onSave, match }: RecordScoreDialogProps) => {
  const { t } = useTranslation();
  const [homeScore, setHomeScore] = useState<string>("");
  const [awayScore, setAwayScore] = useState<string>("");
  const { saving, error, run, clearError } = useDialogSubmit();

  // 2.2: offer the analysed match's result. If the caller cannot open that match, there is simply no offer.
  const realMatchId = match?.realMatchId ?? "";
  const { data: realMatchData } = useGetMatch(realMatchId, {
    query: { enabled: open && realMatchId.length > 0, retry: false },
  });
  const realMatch = realMatchData as unknown as MatchResponse | undefined;
  const suggestion = match && realMatch ? scoreFromAnalysedMatch(match, realMatch) : null;

  const hasExistingScore = match?.homeScore != null && match?.awayScore != null;

  const reset = () => {
    setHomeScore("");
    setAwayScore("");
    clearError();
  };

  const handleSave = async () => {
    const home = parseInt(homeScore, 10);
    const away = parseInt(awayScore, 10);
    if (isNaN(home) || isNaN(away) || home < 0 || away < 0) return;
    if (await run(() => onSave(home, away))) {
      reset();
    }
  };

  const handleClose = () => {
    if (saving) return;
    reset();
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="xs">
      <DialogTitle sx={{ fontWeight: "bold" }}>{t("cups.record-score")}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2}>
          <Typography variant="body2" sx={{ color: "text.secondary" }}>
            {match?.homeTeam?.name} vs {match?.awayTeam?.name}
          </Typography>

          {error && <Alert severity="error">{error}</Alert>}

          {hasExistingScore && (
            <Alert severity="warning">
              {t("cups.score-overwrite-warning", {
                current: `${match.homeScore} – ${match.awayScore}`,
              })}
            </Alert>
          )}

          {suggestion && (
            <Alert
              severity="info"
              action={
                <Button
                  color="inherit"
                  size="small"
                  onClick={() => {
                    setHomeScore(String(suggestion.home));
                    setAwayScore(String(suggestion.away));
                  }}
                >
                  {t("cups.use-analysed-score")}
                </Button>
              }
            >
              {t("cups.analysed-score", { score: `${suggestion.home}–${suggestion.away}` })}
            </Alert>
          )}

          <Box sx={{ display: "flex", gap: 2, alignItems: "center" }}>
            <TextField
              label={t("cups.home-score")}
              type="number"
              value={homeScore}
              onChange={(e) => setHomeScore(e.target.value)}
              slotProps={{ htmlInput: { min: 0 } }}
              fullWidth
            />
            <Typography variant="h6" sx={{ flexShrink: 0 }}>
              -
            </Typography>
            <TextField
              label={t("cups.away-score")}
              type="number"
              value={awayScore}
              onChange={(e) => setAwayScore(e.target.value)}
              slotProps={{ htmlInput: { min: 0 } }}
              fullWidth
            />
          </Box>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        <SecondaryButton onClick={handleClose} disabled={saving}>{t("common.cancel")}</SecondaryButton>
        <PrimaryButton onClick={handleSave} variant="contained" loading={saving}>
          {t("common.save")}
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
