import { useEffect, useState } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Stack,
  Box,
} from "@mui/material";
import { useTranslation } from "react-i18next";
import { PrimaryButton } from "../../../components/ui/PrimaryButton";
import { SecondaryButton } from "../../../components/ui/SecondaryButton";
import { DangerButton } from "../../../components/ui/DeleteButton";
import { ConfirmDeleteDialog } from "../../admin/dialogs/ConfirmDeleteDialog";
import { type ClubResponse, type UpdateClubRequest } from "../../../api/generated/model";

export type ClubUpdateBody = Omit<UpdateClubRequest, "logoUrl"> & {
  logoUrl: string | null;
};

interface EditClubDialogProps {
  open: boolean;
  onClose: () => void;
  onEdit: (id: string, clubData: ClubUpdateBody) => void;
  club: ClubResponse;
  onDelete: (id: string) => void;
}

export const EditClubDialog = ({
  open,
  onClose,
  onEdit,
  onDelete,
  club,
}: EditClubDialogProps) => {
  const { t } = useTranslation();
  const [formData, setFormData] = useState({
    name: club.name ?? "",
    shortName: club.shortName ?? "",
  });
  const [logoUrl, setLogoUrl] = useState(club.logoUrl ?? "");
  const [nameError, setNameError] = useState(false);
  const [isConfirmDeleteOpen, setIsConfirmDeleteOpen] = useState(false);

  useEffect(() => {
    setFormData({ name: club.name ?? "", shortName: club.shortName ?? "" });
    setLogoUrl(club.logoUrl ?? "");
    setNameError(false);
  }, [club, open]);

  const handleSubmit = () => {
    if (!formData.name || formData.name.trim() === "") {
      setNameError(true);
      return;
    }
    const logo = logoUrl.trim();
    onEdit(club.id ?? "", {
      name: formData.name.trim(),
      shortName: formData.shortName.trim() || undefined,
      logoUrl: logo.length === 0 ? null : logo,
    });
    onClose();
  };

  return (
    <>
      <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
        <DialogTitle sx={{ fontWeight: "bold" }}>{t("clubs.edit-club")}</DialogTitle>
        <DialogContent dividers>
          <Stack spacing={3} sx={{ mt: 1 }}>
            <TextField
              fullWidth
              label={t("clubs.name-label")}
              value={formData.name}
              onChange={(e) => {
                setNameError(false);
                setFormData((prev) => ({ ...prev, name: e.target.value }));
              }}
              variant="outlined"
              error={nameError}
              helperText={nameError ? t("clubs.name-error") : ""}
            />
            <TextField
              fullWidth
              label={t("clubs.short-name")}
              value={formData.shortName}
              onChange={(e) => setFormData((prev) => ({ ...prev, shortName: e.target.value }))}
              variant="outlined"
            />
            <TextField
              fullWidth
              label={t("clubs.logo-url")}
              value={logoUrl}
              onChange={(e) => setLogoUrl(e.target.value)}
              variant="outlined"
              slotProps={{ htmlInput: { maxLength: 500 } }}
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ p: 2, justifyContent: "space-between" }}>
          <DangerButton onClick={() => setIsConfirmDeleteOpen(true)}>
            {t("common.delete")}
          </DangerButton>
          <Box>
            <SecondaryButton onClick={onClose} sx={{ mr: 1 }}>
              {t("common.cancel")}
            </SecondaryButton>
            <PrimaryButton onClick={handleSubmit} variant="contained">
              {t("common.save")}
            </PrimaryButton>
          </Box>
        </DialogActions>
      </Dialog>

      <ConfirmDeleteDialog
        open={isConfirmDeleteOpen}
        title={t("clubs.delete-club")}
        description={t("clubs.delete-club-confirm")}
        hold
        onConfirm={() => {
          onDelete(club.id ?? "");
          onClose();
        }}
        onClose={() => setIsConfirmDeleteOpen(false)}
      />
    </>
  );
};
