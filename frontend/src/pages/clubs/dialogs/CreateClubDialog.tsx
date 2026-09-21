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
import { type CreateClubRequest } from "../../../api/generated/model";

export const CreateClubDialog = ({
  open,
  onClose,
  onCreate,
  serverError,
  onClearError,
}: {
  open: boolean;
  onClose: () => void;
  onCreate: (clubData: CreateClubRequest) => void;
  serverError?: string | null;
  onClearError?: () => void;
}) => {
  const { t } = useTranslation();
  const [formData, setFormData] = useState<CreateClubRequest>({ name: "", shortName: "" });
  const [nameError, setNameError] = useState(false);

  useEffect(() => {
    if (!open) {
      setFormData({ name: "", shortName: "" });
      setNameError(false);
    }
  }, [open]);

  const handleSubmit = () => {
    if (formData.name.trim() === "") {
      setNameError(true);
      return;
    }
    onCreate({
      name: formData.name.trim(),
      shortName: formData.shortName?.trim() || undefined,
    });
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontWeight: "bold" }}>{t("clubs.create-club")}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={3} sx={{ mt: 1 }}>
          <TextField
            fullWidth
            label={t("clubs.name-label")}
            value={formData.name}
            onChange={(e) => {
              setNameError(false);
              onClearError?.();
              setFormData((prev) => ({ ...prev, name: e.target.value }));
            }}
            variant="outlined"
            error={nameError}
            helperText={nameError ? t("clubs.name-error") : ""}
          />
          <TextField
            fullWidth
            label={t("clubs.short-name")}
            value={formData.shortName ?? ""}
            onChange={(e) => setFormData((prev) => ({ ...prev, shortName: e.target.value }))}
            variant="outlined"
          />
          {serverError && <Alert severity="error">{serverError}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        <SecondaryButton onClick={onClose}>{t("common.cancel")}</SecondaryButton>
        <PrimaryButton onClick={handleSubmit} variant="contained">
          {t("common.create")}
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
