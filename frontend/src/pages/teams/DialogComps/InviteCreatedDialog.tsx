import { useState } from "react";
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  IconButton,
  TextField,
  Button,
  Typography,
  Snackbar,
  Box,
} from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import { useTranslation } from "react-i18next";

interface InviteCreatedDialogProps {
  inviteUrl: string;
  open: boolean;
  onClose: () => void;
}

export const InviteCreatedDialog = ({
  inviteUrl,
  open,
  onClose,
}: InviteCreatedDialogProps) => {
  const { t } = useTranslation();
  const [snackbarOpen, setSnackbarOpen] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(inviteUrl);
    setSnackbarOpen(true);
  };

  return (
    <>
      <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
        <DialogTitle sx={{ m: 0, p: 2, fontWeight: "bold" }}>
          {t("invite.created-title")}
          <IconButton
            aria-label="close"
            onClick={onClose}
            sx={{
              position: "absolute",
              right: 8,
              top: 8,
              color: (theme) => theme.palette.grey[500],
            }}
          >
            <CloseIcon />
          </IconButton>
        </DialogTitle>

        <DialogContent dividers>
          <Typography variant="body2" gutterBottom>
            {t("invite.send-link")}
          </Typography>
          <Box sx={{ display: "flex", mt: 1 }}>
            <TextField
              fullWidth
              variant="outlined"
              size="small"
              value={inviteUrl}
              slotProps={{
                input: {
                  readOnly: true,
                },
              }}
            />
          </Box>
        </DialogContent>

        <DialogActions sx={{ p: 2 }}>
          <Button
            fullWidth
            variant="contained"
            startIcon={<ContentCopyIcon />}
            onClick={handleCopy}
          >
            {t("invite.copy")}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackbarOpen}
        autoHideDuration={2000}
        onClose={() => setSnackbarOpen(false)}
        message={t("invite.copied")}
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      />
    </>
  );
};
