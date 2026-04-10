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
  const [snackbarOpen, setSnackbarOpen] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(inviteUrl);
    setSnackbarOpen(true);
  };

  return (
    <>
      <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
        {/* Cím és Bezárás gomb */}
        <DialogTitle sx={{ m: 0, p: 2, fontWeight: "bold" }}>
          Meghívó létrehozva
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
            Küldd el ezt a linket a barátaidnak:
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
            Másolás
          </Button>
        </DialogActions>
      </Dialog>

      {/* Egyszerű visszajelzés másoláskor */}
      <Snackbar
        open={snackbarOpen}
        autoHideDuration={2000}
        onClose={() => setSnackbarOpen(false)}
        message="Link kimásolva a vágólapra!"
        anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      />
    </>
  );
};
