import { useState, useEffect } from "react";
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
import { type CupTeamResponse, type TeamResponse } from "../../../api/generated/model";

interface EditCupTeamDialogProps {
  open: boolean;
  onClose: () => void;
  onSave: (name: string, realTeamId?: string) => void;
  team: CupTeamResponse | null;
  myTeams: TeamResponse[];
}

export const EditCupTeamDialog = ({ open, onClose, onSave, team, myTeams }: EditCupTeamDialogProps) => {
  const { t } = useTranslation();
  const [name, setName] = useState("");
  const [realTeamId, setRealTeamId] = useState("");
  const [nameError, setNameError] = useState(false);

  useEffect(() => {
    if (team) {
      setName(team.name ?? "");
      setRealTeamId(team.realTeam?.id ?? "");
    }
  }, [team]);

  const handleTeamSelect = (id: string) => {
    setRealTeamId(id);
    if (id) {
      const found = myTeams.find((tm) => tm.id === id);
      if (found?.name) setName(found.name);
    }
  };

  const handleSubmit = () => {
    if (name.trim() === "") {
      setNameError(true);
      return;
    }
    onSave(name.trim(), realTeamId || undefined);
  };

  const handleClose = () => {
    setNameError(false);
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontWeight: "bold" }}>{t("admin.edit-cup-team")}</DialogTitle>
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
                {myTeams.map((tm) => (
                  <MenuItem key={tm.id} value={tm.id ?? ""}>
                    {tm.name}
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
          {t("common.save")}
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
