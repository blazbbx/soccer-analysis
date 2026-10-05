import React, { useState } from "react";
import { TextField, Stack } from "@mui/material";
import { useTranslation } from "react-i18next";
import { FormDialog } from "../../../components/ui/FormDialog";
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
    setFormData({ name: '' });
    onCreate(formData);
  };

  return (
    <FormDialog
      open={open}
      title={t("teams.create-team")}
      onClose={onClose}
      onSubmit={handleSubmit}
      submitLabel={t("common.create")}
      error={serverError}
    >
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
    </FormDialog>
  );
};
