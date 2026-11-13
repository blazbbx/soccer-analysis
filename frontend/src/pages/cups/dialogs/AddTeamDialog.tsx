import { useState } from "react";
import {
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Stack,
} from "@mui/material";
import { useTranslation } from "react-i18next";
import { PrimaryButton } from "../../../components/ui/PrimaryButton";
import { SecondaryButton } from "../../../components/ui/SecondaryButton";
import { type TeamResponse } from "../../../api/generated/model";
import { TeamAutocomplete } from "../../../components/TeamAutocomplete";
import { useDialogSubmit } from "./useDialogSubmit";

interface AddTeamDialogProps {
  open: boolean;
  onClose: () => void;
  onAdd: (name: string, realTeamId?: string) => Promise<unknown>;
  myTeams: TeamResponse[];
}

export const AddTeamDialog = ({ open, onClose, onAdd, myTeams }: AddTeamDialogProps) => {
  const { t } = useTranslation();
  const [name, setName] = useState("");
  const [realTeamId, setRealTeamId] = useState("");
  const [nameError, setNameError] = useState(false);
  const { saving, error, run, clearError } = useDialogSubmit();

  const handleTeamSelect = (id: string, teamName?: string | null) => {
    setRealTeamId(id);
    if (teamName) setName(teamName);
  };

  const reset = () => {
    setName("");
    setRealTeamId("");
    setNameError(false);
    clearError();
  };

  const handleSubmit = async () => {
    if (name.trim() === "") {
      setNameError(true);
      return;
    }
    if (await run(() => onAdd(name.trim(), realTeamId || undefined))) {
      reset();
    }
  };

  const handleClose = () => {
    if (saving) return;
    reset();
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontWeight: "bold" }}>{t("cups.add-team")}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={3} sx={{ mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <TeamAutocomplete
            teams={myTeams}
            valueId={realTeamId || null}
            onChange={(teamId, team) => handleTeamSelect(teamId ?? "", team?.name)}
            label={t("cups.link-real-team")}
            placeholder={t("cups.no-team-link")}
          />
          <TextField
            fullWidth
            label={t("cups.team-name")}
            value={name}
            onChange={(e) => {
              setName(e.target.value);
              setNameError(false);
            }}
            error={nameError}
            helperText={nameError ? t("cups.name-error") : ""}
            variant="outlined"
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        <SecondaryButton onClick={handleClose} disabled={saving}>{t("common.cancel")}</SecondaryButton>
        <PrimaryButton onClick={handleSubmit} variant="contained" loading={saving}>
          {t("common.add")}
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
