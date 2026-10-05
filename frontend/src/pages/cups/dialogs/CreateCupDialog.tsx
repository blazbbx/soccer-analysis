import { useState } from "react";
import { TextField, Stack } from "@mui/material";
import { useTranslation } from "react-i18next";
import { FormDialog } from "../../../components/ui/FormDialog";

interface CreateCupDialogProps {
  open: boolean;
  onClose: () => void;
  onCreate: (name: string) => void;
}

export const CreateCupDialog = ({ open, onClose, onCreate }: CreateCupDialogProps) => {
  const { t } = useTranslation();
  const [name, setName] = useState("");
  const [nameError, setNameError] = useState(false);

  const handleSubmit = () => {
    if (name.trim() === "") {
      setNameError(true);
      return;
    }
    onCreate(name.trim());
    setName("");
    setNameError(false);
  };

  const handleClose = () => {
    setName("");
    setNameError(false);
    onClose();
  };

  return (
    <FormDialog
      open={open}
      title={t("cups.create-cup")}
      onClose={handleClose}
      onSubmit={handleSubmit}
      submitLabel={t("common.create")}
    >
      <Stack spacing={3} sx={{ mt: 1 }}>
        <TextField
          fullWidth
          label={t("cups.cup-name")}
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
    </FormDialog>
  );
};
