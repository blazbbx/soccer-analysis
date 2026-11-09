import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from "@mui/material";
import { useTranslation } from "react-i18next";

/** A plain yes-or-no for deleting a homework: only one without any hand-in can go, so nothing of a player's is lost. */
export const ConfirmDeleteDialog = ({
  open,
  title,
  body,
  pending,
  onClose,
  onConfirm,
}: {
  open: boolean;
  title: string;
  body: string;
  pending?: boolean;
  onClose: () => void;
  onConfirm: () => void;
}) => {
  const { t } = useTranslation();
  return (
    <Dialog open={open} onClose={pending ? undefined : onClose} fullWidth maxWidth="xs">
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText>{body}</DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={pending}>{t("homework.editor.cancel")}</Button>
        <Button variant="contained" color="error" disabled={pending} onClick={onConfirm}>{t("homework.delete-confirm")}</Button>
      </DialogActions>
    </Dialog>
  );
};
