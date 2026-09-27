import { useEffect, useState } from "react";
import {
  Alert,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
} from "@mui/material";
import { useTranslation } from "react-i18next";
import { PrimaryButton } from "../../../components/ui/PrimaryButton";
import { SecondaryButton } from "../../../components/ui/SecondaryButton";
import { type CreatePlayerProfileRequest } from "../../../api/playerProfiles";

export const CreatePlayerProfileDialog = ({
  open,
  onClose,
  onCreate,
  serverError,
  onClearError,
}: {
  open: boolean;
  onClose: () => void;
  onCreate: (data: CreatePlayerProfileRequest) => void;
  serverError?: string | null;
  onClearError?: () => void;
}) => {
  const { t } = useTranslation();
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [firstNameError, setFirstNameError] = useState(false);
  const [lastNameError, setLastNameError] = useState(false);

  useEffect(() => {
    if (!open) return;
    setFirstName("");
    setLastName("");
    setDateOfBirth("");
    setFirstNameError(false);
    setLastNameError(false);
  }, [open]);

  const handleSubmit = () => {
    const trimmedFirst = firstName.trim();
    const trimmedLast = lastName.trim();
    setFirstNameError(trimmedFirst === "");
    setLastNameError(trimmedLast === "");
    if (trimmedFirst === "" || trimmedLast === "") return;
    onCreate({
      firstName: trimmedFirst,
      lastName: trimmedLast,
      dateOfBirth: dateOfBirth || undefined,
    });
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle sx={{ fontWeight: "bold" }}>{t("teams.add-player-title")}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {serverError && (
            <Alert severity="error" onClose={onClearError}>
              {serverError}
            </Alert>
          )}
          <TextField
            label={t("teams.first-name")}
            value={firstName}
            onChange={(event) => {
              setFirstName(event.target.value);
              setFirstNameError(false);
            }}
            error={firstNameError}
            helperText={firstNameError ? t("teams.first-name-error") : undefined}
            fullWidth
            autoFocus
          />
          <TextField
            label={t("teams.last-name")}
            value={lastName}
            onChange={(event) => {
              setLastName(event.target.value);
              setLastNameError(false);
            }}
            error={lastNameError}
            helperText={lastNameError ? t("teams.last-name-error") : undefined}
            fullWidth
          />
          <TextField
            label={t("teams.date-of-birth")}
            type="date"
            value={dateOfBirth}
            onChange={(event) => setDateOfBirth(event.target.value)}
            InputLabelProps={{ shrink: true }}
            fullWidth
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        <SecondaryButton onClick={onClose}>{t("common.cancel")}</SecondaryButton>
        <PrimaryButton onClick={handleSubmit}>{t("common.add")}</PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
