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
import { PrimaryButton } from "../../components/ui/PrimaryButton";
import { SecondaryButton } from "../../components/ui/SecondaryButton";
import type { ChildResponse, CreateChildLoginRequest } from "../../api/children";

export const CreateChildLoginDialog = ({
  open,
  child,
  onClose,
  onCreate,
  submitting,
  serverError,
  onClearError,
}: {
  open: boolean;
  child: ChildResponse | null;
  onClose: () => void;
  onCreate: (data: CreateChildLoginRequest) => void;
  submitting?: boolean;
  serverError?: string | null;
  onClearError?: () => void;
}) => {
  const { t } = useTranslation();
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!open || !child) return;
    setFirstName(child.firstName);
    setLastName(child.lastName);
    setEmail("");
    setPassword("");
    setErrors({});
  }, [open, child]);

  const validate = () => {
    const next: Record<string, string> = {};
    if (!firstName.trim()) next.firstName = t("registration.first-name-required");
    if (!lastName.trim()) next.lastName = t("registration.last-name-required");
    if (!email.trim()) next.email = t("registration.email-required");
    else if (!/\S+@\S+\.\S+/.test(email)) next.email = t("registration.email-invalid");
    if (!password) next.password = t("registration.password-required");
    else if (password.length < 6) next.password = t("registration.password-length");
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = () => {
    if (!validate()) return;
    onCreate({
      email: email.trim(),
      password,
      firstName: firstName.trim(),
      lastName: lastName.trim(),
    });
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontWeight: "bold" }}>
        {t("children.child-login-dialog-title")}
      </DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {serverError && (
            <Alert severity="error" onClose={onClearError}>
              {serverError}
            </Alert>
          )}
          <TextField
            label={t("registration.child-first-name")}
            value={firstName}
            onChange={(event) => {
              setFirstName(event.target.value);
              setErrors((prev) => ({ ...prev, firstName: "" }));
            }}
            error={!!errors.firstName}
            helperText={errors.firstName}
            fullWidth
          />
          <TextField
            label={t("registration.child-last-name")}
            value={lastName}
            onChange={(event) => {
              setLastName(event.target.value);
              setErrors((prev) => ({ ...prev, lastName: "" }));
            }}
            error={!!errors.lastName}
            helperText={errors.lastName}
            fullWidth
          />
          <TextField
            label={t("children.child-login-email")}
            value={email}
            onChange={(event) => {
              setEmail(event.target.value);
              setErrors((prev) => ({ ...prev, email: "" }));
              onClearError?.();
            }}
            error={!!errors.email}
            helperText={errors.email}
            fullWidth
            type="email"
            autoFocus
          />
          <TextField
            label={t("children.child-login-password")}
            value={password}
            onChange={(event) => {
              setPassword(event.target.value);
              setErrors((prev) => ({ ...prev, password: "" }));
            }}
            error={!!errors.password}
            helperText={errors.password}
            fullWidth
            type="password"
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        <SecondaryButton onClick={onClose} disabled={submitting}>
          {t("common.cancel")}
        </SecondaryButton>
        <PrimaryButton onClick={handleSubmit} disabled={submitting}>
          {submitting ? t("registration.creating") : t("children.child-login-submit")}
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
