import { useEffect, useState } from "react";
import {
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Stack,
  MenuItem,
} from "@mui/material";
import { useTranslation } from "react-i18next";
import { PrimaryButton } from "../../../components/ui/PrimaryButton";
import { SecondaryButton } from "../../../components/ui/SecondaryButton";
import { type CreateTeamRequest } from "../../../api/generated/model";
import { useListClubs } from "../../../api/generated/clubs/clubs";

export const CreateTeamDialog = ({
  open,
  onClose,
  onCreate,
  serverError,
  onClearError,
  initialClubId,
  lockClub = false,
}: {
  open: boolean;
  onClose: () => void;
  onCreate: (teamData: CreateTeamRequest) => void;
  serverError?: string | null;
  onClearError?: () => void;
  initialClubId?: string;
  lockClub?: boolean;
}) => {
  const { t } = useTranslation();
  const { data: clubs = [], isLoading: isLoadingClubs } = useListClubs({
    query: { enabled: open && !lockClub },
  });

  const [formData, setFormData] = useState<CreateTeamRequest>({
    name: "",
    clubId: initialClubId,
  });

  const [nameError, setNameError] = useState(false);
  const [clubError, setClubError] = useState(false);

  useEffect(() => {
    if (!open) return;
    setFormData({ name: "", clubId: initialClubId });
    setNameError(false);
    setClubError(false);
  }, [open, initialClubId]);

  useEffect(() => {
    if (!open || lockClub || initialClubId) return;
    if (clubs.length === 1 && clubs[0].id) {
      setFormData((prev) => ({ ...prev, clubId: clubs[0].id }));
    }
  }, [open, lockClub, initialClubId, clubs]);

  const resolvedClubId =
    formData.clubId || (clubs.length === 1 ? clubs[0].id : undefined);

  const handleSubmit = () => {
    if (formData.name.trim() === "") {
      setNameError(true);
      return;
    }
    if (!lockClub && !resolvedClubId) {
      setClubError(true);
      return;
    }
    onCreate({
      name: formData.name.trim(),
      clubId: resolvedClubId || undefined,
    });
  };

  const canSubmit = lockClub || Boolean(resolvedClubId);

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
            onChange={(e) => {
              setNameError(false);
              onClearError?.();
              setFormData((prev) => ({ ...prev, name: e.target.value }));
            }}
            variant="outlined"
            error={nameError}
            helperText={nameError ? t("teams.name-error") : ""}
          />
          {!lockClub && isLoadingClubs && (
            <Alert severity="info">{t("common.loading")}</Alert>
          )}
          {!lockClub && !isLoadingClubs && clubs.length === 0 && (
            <Alert severity="warning">{t("teams.club-required-empty")}</Alert>
          )}
          {!lockClub && clubs.length > 0 && (
            <TextField
              select
              fullWidth
              required
              label={t("teams.club-label")}
              value={formData.clubId ?? ""}
              onChange={(e) => {
                setClubError(false);
                setFormData((prev) => ({
                  ...prev,
                  clubId: e.target.value || undefined,
                }));
              }}
              disabled={clubs.length === 1}
              error={clubError}
              helperText={
                clubError
                  ? t("teams.club-required")
                  : clubs.length === 1
                    ? t("teams.club-helper-single")
                    : t("teams.club-helper")
              }
            >
              {clubs.length > 1 && (
                <MenuItem value="" disabled>
                  {t("teams.club-placeholder")}
                </MenuItem>
              )}
              {clubs.map((club) => (
                <MenuItem key={club.id} value={club.id}>
                  {club.name}
                </MenuItem>
              ))}
            </TextField>
          )}
          {serverError && (
            <Alert severity="error">{serverError}</Alert>
          )}
        </Stack>
      </DialogContent>

      <DialogActions sx={{ p: 2 }}>
        <SecondaryButton onClick={onClose}>
          {t("common.cancel")}
        </SecondaryButton>
        <PrimaryButton
          onClick={handleSubmit}
          variant="contained"
          disabled={!lockClub && (isLoadingClubs || !canSubmit)}
        >
          {t("common.create")}
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
