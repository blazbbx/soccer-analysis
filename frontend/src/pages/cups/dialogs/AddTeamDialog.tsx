import { useState } from "react";
import {
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

interface AddTeamDialogProps {
  open: boolean;
  onClose: () => void;
  onAdd: (name: string, realTeamId?: string) => void;
  myTeams: TeamResponse[];
}

export const AddTeamDialog = ({ open, onClose, onAdd, myTeams }: AddTeamDialogProps) => {
  const { t } = useTranslation();
  const [name, setName] = useState("");
  const [realTeamId, setRealTeamId] = useState("");
  const [nameError, setNameError] = useState(false);

  const handleTeamSelect = (id: string, teamName?: string | null) => {
    setRealTeamId(id);
    if (teamName) setName(teamName);
  };

  const handleSubmit = () => {
    if (name.trim() === "") {
      setNameError(true);
      return;
    }
    onAdd(name.trim(), realTeamId || undefined);
    setName("");
    setRealTeamId("");
    setNameError(false);
  };

  const handleClose = () => {
    setName("");
    setRealTeamId("");
    setNameError(false);
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontWeight: "bold" }}>{t("cups.add-team")}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={3} sx={{ mt: 1 }}>
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
        <SecondaryButton onClick={handleClose}>{t("common.cancel")}</SecondaryButton>
        <PrimaryButton onClick={handleSubmit} variant="contained">
          {t("common.add")}
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
