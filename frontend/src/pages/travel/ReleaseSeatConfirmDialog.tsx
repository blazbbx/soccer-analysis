import { Dialog, DialogActions, DialogContent, DialogTitle, Typography } from "@mui/material";
import { useTranslation } from "react-i18next";
import { PrimaryButton } from "../../components/ui/PrimaryButton";
import { SecondaryButton } from "../../components/ui/SecondaryButton";
import type { AttendanceStatus } from "../../api/travel";

export const ReleaseSeatConfirmDialog = ({
  open,
  self,
  personName,
  status,
  pending,
  onCancel,
  onConfirm,
}: {
  open: boolean;
  self: boolean;
  personName: string;
  status: Extract<AttendanceStatus, "NOT_GOING" | "MAYBE"> | "NO_RIDE";
  pending?: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}) => {
  const { t } = useTranslation();
  const bodyKey = status === "NO_RIDE"
    ? self ? "travel.release-seat.self-no-ride" : "travel.release-seat.other-no-ride"
    : self
      ? status === "MAYBE"
        ? "travel.release-seat.self-maybe"
        : "travel.release-seat.self-not-going"
      : status === "MAYBE"
        ? "travel.release-seat.other-maybe"
        : "travel.release-seat.other-not-going";
  const confirmKey = status === "NO_RIDE"
    ? "travel.no-ride"
    : status === "MAYBE"
      ? "travel.status.maybe"
      : "travel.release-seat.confirm-not-going";

  return (
    <Dialog open={open} onClose={pending ? undefined : onCancel} fullWidth maxWidth="xs">
      <DialogTitle>{t("travel.release-seat.title")}</DialogTitle>
      <DialogContent>
        <Typography>{t(bodyKey, { name: personName })}</Typography>
      </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        <SecondaryButton onClick={onCancel} disabled={pending}>
          {t("common.cancel")}
        </SecondaryButton>
        <PrimaryButton onClick={onConfirm} disabled={pending}>
          {t(confirmKey)}
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
