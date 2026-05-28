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
import { useTranslation } from "react-i18next";

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
  const { t } = useTranslation();

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle sx={{ m: 0, p: 2, fontWeight: "bold" }}>
        {t("invite.type-title")}
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
          {t("invite.type-description")}
        </Typography>
      </DialogContent>

      <DialogActions sx={{ p: 2 }}>
        <Stack direction="row" spacing={2} sx={{ width: "100%" }}>
          <Button
            fullWidth
            variant="outlined"
            onClick={() => onSelectRole("PLAYER")}
          >
            {t("invite.player")}
          </Button>
          <Button
            fullWidth
            variant="contained"
            onClick={() => onSelectRole("FAN")}
          >
            {t("invite.fan")}
          </Button>
        </Stack>
      </DialogActions>
    </Dialog>
  );
};
