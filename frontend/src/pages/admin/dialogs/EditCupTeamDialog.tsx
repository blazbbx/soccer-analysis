import { useState, useEffect } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Alert,
  Stack,
} from "@mui/material";
import { useTranslation } from "react-i18next";
import { useDialogSubmit } from "../../cups/dialogs/useDialogSubmit";
import { PrimaryButton } from "../../../components/ui/PrimaryButton";
import { SecondaryButton } from "../../../components/ui/SecondaryButton";
import { type CupTeamResponse, type TeamResponse } from "../../../api/generated/model";
import { TeamAutocomplete } from "../../../components/TeamAutocomplete";

interface EditCupTeamDialogProps {
  open: boolean;
  onClose: () => void;
  onSave: (name: string, realTeamId?: string) => Promise<unknown>;
  team: CupTeamResponse | null;
  myTeams: TeamResponse[];
}

export const EditCupTeamDialog = ({ open, onClose, onSave, team, myTeams }: EditCupTeamDialogProps) => {
  const { t } = useTranslation();
  const [name, setName] = useState("");
  const [realTeamId, setRealTeamId] = useState("");
  const [nameError, setNameError] = useState(false);
  const { saving, error, run, clearError } = useDialogSubmit();

  useEffect(() => {
    if (team) {
      setName(team.name ?? "");
      setRealTeamId(team.realTeam?.id ?? "");
    }
  }, [team]);

  const handleTeamSelect = (id: string, teamName?: string | null) => {
    setRealTeamId(id);
    if (teamName) setName(teamName);
  };

  const handleSubmit = async () => {
    if (name.trim() === "") {
      setNameError(true);
      return;
    }
    await run(() => onSave(name.trim(), realTeamId || undefined));
  };

  const handleClose = () => {
    if (saving) return;
    clearError();
    setNameError(false);
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontWeight: "bold" }}>{t("admin.edit-cup-team")}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={3} sx={{ mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <TeamAutocomplete
            teams={myTeams}
            valueId={realTeamId || null}
            selectedLabel={team?.realTeam?.name ?? team?.name}
            onChange={(teamId, picked) => handleTeamSelect(teamId ?? "", picked?.name)}
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
          {t("common.save")}
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
