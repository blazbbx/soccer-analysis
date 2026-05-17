import { useState } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Stack,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
} from "@mui/material";
import { useTranslation } from "react-i18next";
import { PrimaryButton } from "../../../components/ui/PrimaryButton";
import { SecondaryButton } from "../../../components/ui/SecondaryButton";
import { type TeamResponse } from "../../../api/generated/model";

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

  const handleTeamSelect = (id: string) => {
    setRealTeamId(id);
    if (id) {
      const team = myTeams.find((tm) => tm.id === id);
      if (team?.name) setName(team.name);
    }
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
          {myTeams.length > 0 && (
            <FormControl fullWidth variant="outlined">
              <InputLabel>{t("cups.link-real-team")}</InputLabel>
              <Select
                value={realTeamId}
                onChange={(e) => handleTeamSelect(e.target.value)}
                label={t("cups.link-real-team")}
              >
                <MenuItem value="">{t("cups.no-team-link")}</MenuItem>
                {myTeams.map((team) => (
                  <MenuItem key={team.id} value={team.id ?? ""}>
                    {team.name}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          )}
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
