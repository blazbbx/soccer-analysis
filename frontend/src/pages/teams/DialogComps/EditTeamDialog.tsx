import React, { useState, useEffect } from "react";
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
import { type TeamResponse, type UpdateTeamRequest } from "../../../api/generated/model";

interface EditTeamDialogProps {
  open: boolean;
  onClose: () => void;
  onEdit: (id: string, teamData: UpdateTeamRequest) => void;
  team: TeamResponse;
  onDelete: (id: string) => void;
}

export const EditTeamDialog = ({ open, onClose, onEdit, onDelete, team }: EditTeamDialogProps) => {
  const { t } = useTranslation();
  const [formData, setFormData] = useState<UpdateTeamRequest>({ name: team.name ?? "" });
  const [nameError, setNameError] = useState(false);
  const [isConfirmDeleteOpen, setIsConfirmDeleteOpen] = useState(false);

  useEffect(() => {
    setFormData({ name: team.name ?? "" });
  }, [team]);

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    if (name === "name") setNameError(false);
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  const handleDeleteConfirmed = () => {
    onDelete(team.id ?? "");
    onClose();
  };

  const handleSubmit = () => {
    if (!formData.name || formData.name.trim() === "") {
      setNameError(true);
      return;
    }
    onEdit(team.id ?? "", formData);
    onClose();
  };

  return (
    <>
      <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
        <DialogTitle sx={{ fontWeight: "bold" }}>{t("teams.edit-team")}</DialogTitle>
        <DialogContent dividers>
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
        </DialogContent>
        <DialogActions sx={{ p: 2, justifyContent: "space-between" }}>
          <DangerButton onClick={() => setIsConfirmDeleteOpen(true)}>
            {t("common.delete")}
          </DangerButton>
          <Box>
            <SecondaryButton onClick={onClose} sx={{ mr: 1 }}>{t("common.cancel")}</SecondaryButton>
            <PrimaryButton onClick={handleSubmit} variant="contained">
              {t("common.save")}
            </PrimaryButton>
          </Box>
        </DialogActions>
      </Dialog>

      <ConfirmDeleteDialog
        open={isConfirmDeleteOpen}
        title={t("admin.delete-team")}
        description={t("admin.delete-team-confirm")}
        onConfirm={handleDeleteConfirmed}
        onClose={() => setIsConfirmDeleteOpen(false)}
      />
    </>
  );
};
