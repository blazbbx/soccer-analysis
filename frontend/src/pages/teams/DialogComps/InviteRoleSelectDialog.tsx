import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  IconButton,
  Button,
  Typography,
  Stack,
} from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";

interface InviteRoleSelectDialogProps {
  open: boolean;
  onClose: () => void;
  onSelectRole: (role: "PLAYER" | "FAN") => void;
}

export const InviteRoleSelectDialog = ({
  open,
  onClose,
  onSelectRole,
}: InviteRoleSelectDialogProps) => {
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle sx={{ m: 0, p: 2, fontWeight: "bold" }}>
        Meghívó típusa
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
        <Typography variant="body2">
          Válaszd ki, milyen szerepkört kapjon a meghívott felhasználó.
        </Typography>
      </DialogContent>

      <DialogActions sx={{ p: 2 }}>
        <Stack direction="row" spacing={2} sx={{ width: "100%" }}>
          <Button
            fullWidth
            variant="outlined"
            onClick={() => onSelectRole("PLAYER")}
          >
            Játékos meghívó
          </Button>
          <Button
            fullWidth
            variant="contained"
            onClick={() => onSelectRole("FAN")}
          >
            Szurkoló meghívó
          </Button>
        </Stack>
      </DialogActions>
    </Dialog>
  );
};
