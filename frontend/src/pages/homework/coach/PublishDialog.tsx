import { Alert, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Stack } from "@mui/material";
import { useTranslation } from "react-i18next";

export const PublishDialog = ({
  open,
  entireRoster,
  recipientCount,
  itemCount,
  pending,
  error,
  onClose,
  onConfirm,
}: {
  open: boolean;
  entireRoster: boolean;
  recipientCount: number;
  itemCount: number;
  pending: boolean;
  error?: string | null;
  onClose: () => void;
  onConfirm: () => void;
}) => {
  const { t } = useTranslation();
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>{t("homework.publish.title")}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 0.5 }}>
          <DialogContentText>
            {entireRoster
              ? t("homework.publish.entire", { items: itemCount })
              : t("homework.publish.summary", { count: recipientCount, items: itemCount })}
          </DialogContentText>
          {error && <Alert severity="error">{error}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t("homework.editor.cancel")}</Button>
        <Button variant="contained" disabled={pending} onClick={onConfirm}>
          {t("homework.publish.confirm")}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
