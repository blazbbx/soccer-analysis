import React, { useState } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Stack,
} from "@mui/material";
import { useTranslation } from "react-i18next";
import { PrimaryButton } from "../ui/PrimaryButton";
import { SecondaryButton } from "../ui/SecondaryButton";
import { type CreateTeamRequest } from "../../../api/generated/model";

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

  const [formData, setFormData] = useState<CreateTeamRequest>({
    name: "",    
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
        name === "name" ? value : Number.parseInt(value) || 0,
    }));
  };

  const handleSubmit = () => {
    //Ha a név üres, error
    if (formData.name.trim() === '') {
      setNameError(true); 
      return; 
    }
    setFormData({ name: ''});
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
            error={nameError} 
            helperText={nameError ? t("teams.name-error") : ""}
          />        
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
