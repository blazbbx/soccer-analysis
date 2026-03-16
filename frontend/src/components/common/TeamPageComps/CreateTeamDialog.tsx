import React, { useState } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  MenuItem,
  Stack,
  Typography,
} from "@mui/material";
import { useTranslation } from "react-i18next";
import { PrimaryButton } from "../ui/PrimaryButton";
import { SecondaryButton } from "../ui/SecondaryButton";
import { FORMATIONS } from "../../../constants/formations";
import { CreateTeamRequest } from "../../../types/team";

export const CreateTeamDialog = ({
  open,
  onClose,
  onCreate,
}: {
  open: boolean;
  onClose: () => void;
  onCreate: (teamData: CreateTeamRequest) => void;
}) => {
  const { t } = useTranslation();

  const [formData, setFormData] = useState({
    name: "",
    formation: "2-2",
    wins: 0,
    draws: 0,
    losses: 0,
    points: 0,
    coachName: "Coach"
  });

  //State a kötelező névhez
  const [nameError, setNameError] = useState(false);  

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    if (name === 'name') {
      setNameError(false);
    }
    setFormData((prev) => ({
      ...prev,
      [name]:
        name === "name" || name === "formation" ? value : Number.parseInt(value) || 0,
    }));
  };

  const handleSubmit = () => {
    //Ha a név üres, error
    if (formData.name.trim() === '') {
      setNameError(true); 
      return; 
    }
    setFormData({ name: '', formation: FORMATIONS[0], wins: 0, draws: 0, losses: 0 , points: 0, coachName: "Coach"});
    onCreate(formData);
    onClose();
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontWeight: "bold" }}>
        {t("teams.create-team")}
      </DialogTitle>

      <DialogContent dividers>
        <Stack spacing={3} sx={{ mt: 1 }}>
          <TextField
            fullWidth
            label={t("teams.name-label")}
            name="name"
            value={formData.name}
            onChange={handleChange}
            variant="outlined"
            //Hiba, ha nincs név
            error={nameError} 
            helperText={nameError ? t("teams.name-error") : ""}
          />

          <TextField
            select
            fullWidth
            label={t("teams.formation")}
            name="formation"
            value={formData.formation}
            onChange={handleChange}
          >
            {FORMATIONS.map((option) => (
              <MenuItem key={option} value={option}>
                {option}
              </MenuItem>
            ))}
          </TextField>

          <Typography
            variant="subtitle2"
            sx={{ color: "text.secondary", mb: -1 }}
          >
            {t("teams.stats")}
          </Typography>

          <Stack direction="row" spacing={2}>
            <TextField
              type="number"
              label={t("teams.wins")}
              name="wins"
              value={formData.wins}
              onChange={handleChange}
              slotProps={{
                htmlInput: { min: 0 },
              }}
            />
            <TextField
              type="number"
              label={t("teams.draws")}
              name="draws"
              value={formData.draws}
              onChange={handleChange}
              slotProps={{
                htmlInput: { min: 0 },
              }}
            />
            <TextField
              type="number"
              label={t("teams.losses")}
              name="losses"
              value={formData.losses}
              onChange={handleChange}
              slotProps={{
                htmlInput: { min: 0 },
              }}
            />
          </Stack>
        </Stack>
      </DialogContent>

      <DialogActions sx={{ p: 2 }}>
        <SecondaryButton onClick={onClose}>
          {t("common.cancel")}
        </SecondaryButton>
        <PrimaryButton onClick={handleSubmit} variant="contained">
          {t("common.create")}
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
