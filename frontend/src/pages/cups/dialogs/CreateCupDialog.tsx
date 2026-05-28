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
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontWeight: "bold" }}>{t("cups.create-cup")}</DialogTitle>
      <DialogContent dividers>
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
      </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        <SecondaryButton onClick={handleClose}>{t("common.cancel")}</SecondaryButton>
        <PrimaryButton onClick={handleSubmit} variant="contained">
          {t("common.create")}
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
