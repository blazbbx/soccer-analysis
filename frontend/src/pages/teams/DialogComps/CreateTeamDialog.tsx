import React, { useState } from "react";
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
import { type CreateTeamRequest } from "../../../api/generated/model";

export const CreateTeamDialog = ({
  open,
  onClose,
  onCreate,
  serverError,
  onClearError,
}: {
  open: boolean;
  onClose: () => void;
  onCreate: (teamData: CreateTeamRequest) => void;
  serverError?: string | null;
  onClearError?: () => void;
}) => {
  const { t } = useTranslation();

  const [formData, setFormData] = useState<CreateTeamRequest>({
    name: "",
  });

  const [nameError, setNameError] = useState(false);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    if (name === 'name') {
      setNameError(false);
      onClearError?.();
    }
    setFormData((prev) => ({
      ...prev,
      [name]:
        name === "name" ? value : Number.parseInt(value) || 0,
    }));
  };

  const handleSubmit = () => {
    if (formData.name.trim() === '') {
      setNameError(true);
      return;
    }
    setFormData({ name: ''});
    onCreate(formData);
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
          {serverError && (
            <Alert severity="error">{serverError}</Alert>
          )}
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
