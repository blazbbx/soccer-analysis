import React, { useState, useEffect } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Stack,
  Box,
} from "@mui/material";
import { useTranslation } from "react-i18next";
import { PrimaryButton } from "../../ui/PrimaryButton";
import { SecondaryButton } from "../../ui/SecondaryButton";
import { DangerButton } from "../../ui/DeleteButton";
import { type TeamResponse, type UpdateTeamRequest } from "../../../../api/generated/model";

interface EditTeamDialogProps {
  open: boolean;
  onClose: () => void;
  onEdit: (id: string, teamData: UpdateTeamRequest) => void;
  team: TeamResponse;
  onDelete: (id: string) => void;
}

export const EditTeamDialog = ({ open, onClose, onEdit, onDelete, team }: EditTeamDialogProps) => {
  const { t } = useTranslation();

  const [formData, setFormData] = useState<UpdateTeamRequest>({
    name: team.name ??"",    
  });

  

  const [nameError, setNameError] = useState(false);

  
  useEffect(() => {
    setFormData({
      name: team.name ?? ""      
    });
  }, [team]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    if (name === "name") setNameError(false);
    
    setFormData((prev) => ({
      ...prev,
      [name]: name === "name" || name === "formation" ? value : Number.parseInt(value) || 0,
    }));
  };

  const handleDelete = () => {
    if (window.confirm(t("teams.delete-confirm", "Biztosan törölni szeretnéd ezt a csapatot? Ez a művelet nem vonható vissza."))) {
      onDelete(team.id ?? "");
      onClose();
    }
  };

  const handleSubmit = () => {
    if (!formData.name || formData.name.trim() === "") {
      setNameError(true);
      return;
    }
    onEdit(team.id ?? "", formData);
    onClose();
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontWeight: "bold" }}>{t("teams.edit-team", "Csapat Szerkesztése")}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={3} sx={{ mt: 1 }}>
          <TextField
            fullWidth
            label={t("teams.name-label")}
            name="name"
            value={formData.name}
            onChange={handleChange}
            variant="outlined"
            error={nameError}
            helperText={nameError ? t("teams.name-error", "Név megadása kötelező") : ""}
          />         
        </Stack>
      </DialogContent>
      <DialogActions sx={{ p: 2, justifyContent: "space-between" }}>
        <DangerButton onClick={handleDelete}>
          {t("common.delete", "Törlés")}
        </DangerButton>
        <Box>
          <SecondaryButton onClick={onClose} sx={{ mr: 1 }}>{t("common.cancel")}</SecondaryButton>
          <PrimaryButton onClick={handleSubmit} variant="contained">
            {t("common.save", "Mentés")}
          </PrimaryButton>
        </Box>
      </DialogActions>
    </Dialog>
  );
};