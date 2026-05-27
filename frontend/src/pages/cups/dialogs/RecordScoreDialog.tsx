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
} from "@mui/material";
import { useTranslation } from "react-i18next";
import { PrimaryButton } from "../../../components/ui/PrimaryButton";
import { SecondaryButton } from "../../../components/ui/SecondaryButton";
import { type CupMatchResponse } from "../../../api/generated/model";

interface RecordScoreDialogProps {
  open: boolean;
  onClose: () => void;
  onSave: (homeScore: number, awayScore: number) => void;
  match: CupMatchResponse | null;
}

export const RecordScoreDialog = ({ open, onClose, onSave, match }: RecordScoreDialogProps) => {
  const { t } = useTranslation();
  const [homeScore, setHomeScore] = useState<string>("");
  const [awayScore, setAwayScore] = useState<string>("");

  const hasExistingScore =
    match?.homeScore != null && match?.awayScore != null;

  const handleSave = () => {
    const home = parseInt(homeScore, 10);
    const away = parseInt(awayScore, 10);
    if (isNaN(home) || isNaN(away) || home < 0 || away < 0) return;
    onSave(home, away);
    setHomeScore("");
    setAwayScore("");
  };

  const handleClose = () => {
    setHomeScore("");
    setAwayScore("");
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

          {hasExistingScore && (
            <Alert severity="warning">
              {t("cups.score-overwrite-warning", {
                current: `${match.homeScore} – ${match.awayScore}`,
              })}
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
        <SecondaryButton onClick={handleClose}>{t("common.cancel")}</SecondaryButton>
        <PrimaryButton onClick={handleSave} variant="contained">
          {t("common.save")}
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
