import { useEffect, useState } from "react";
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
import { useDialogSubmit } from "./useDialogSubmit";

interface CreateCupDialogProps {
  open: boolean;
  onClose: () => void;
  onCreate: (name: string) => Promise<unknown>;
  /** Rename mode (2.6): the current name, the dialog title and the button label. */
  initialName?: string;
  title?: string;
  submitLabel?: string;
}

/** Cup name dialog: creates a cup, or renames one when `initialName` is given. */
export const CreateCupDialog = ({ open, onClose, onCreate, initialName, title, submitLabel }: CreateCupDialogProps) => {
  const { t } = useTranslation();
  const [name, setName] = useState(initialName ?? "");
  const [nameError, setNameError] = useState(false);
  const { saving, error, run, clearError } = useDialogSubmit();

  useEffect(() => {
    if (open) setName(initialName ?? "");
  }, [open, initialName]);

  const handleSubmit = async () => {
    if (name.trim() === "") {
      setNameError(true);
      return;
    }
    if (await run(() => onCreate(name.trim()))) {
      setName("");
      setNameError(false);
    }
  };

  const handleClose = () => {
    if (saving) return;
    setName("");
    setNameError(false);
    clearError();
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontWeight: "bold" }}>{title ?? t("cups.create-cup")}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={3} sx={{ mt: 1 }}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField
            fullWidth
            autoFocus
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
        <SecondaryButton onClick={handleClose} disabled={saving}>{t("common.cancel")}</SecondaryButton>
        <PrimaryButton onClick={handleSubmit} variant="contained" loading={saving}>
          {submitLabel ?? t("common.create")}
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
