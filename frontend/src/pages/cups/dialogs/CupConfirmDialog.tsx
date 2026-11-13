import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Typography } from "@mui/material";
import { useTranslation } from "react-i18next";
import { SecondaryButton } from "../../../components/ui/SecondaryButton";
import { useDialogSubmit } from "./useDialogSubmit";

interface CupConfirmDialogProps {
  open: boolean;
  title: string;
  description: string;
  confirmLabel: string;
  onConfirm: () => Promise<unknown>;
  onClose: () => void;
}

/** Confirmation that waits for the request and shows the backend's error instead of closing silently. */
export const CupConfirmDialog = ({ open, title, description, confirmLabel, onConfirm, onClose }: CupConfirmDialogProps) => {
  const { t } = useTranslation();
  const { saving, error, run, clearError } = useDialogSubmit();

  const handleClose = () => {
    if (saving) return;
    clearError();
    onClose();
  };

  const handleConfirm = async () => {
    if (await run(onConfirm)) {
      onClose();
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="xs">
      <DialogTitle sx={{ fontWeight: "bold" }}>{title}</DialogTitle>
      <DialogContent dividers>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <Typography variant="body2">{description}</Typography>
      </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        <SecondaryButton onClick={handleClose} disabled={saving}>{t("common.cancel")}</SecondaryButton>
        <Button onClick={handleConfirm} variant="contained" color="error" loading={saving}
          sx={{ borderRadius: 2, fontWeight: "bold", textTransform: "none" }}>
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
