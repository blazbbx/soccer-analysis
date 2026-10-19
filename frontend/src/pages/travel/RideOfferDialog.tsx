import { useEffect, useState } from "react";
import {
  Alert,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { DateTimePicker } from "@mui/x-date-pickers/DateTimePicker";
import dayjs, { type Dayjs } from "dayjs";
import { useTranslation } from "react-i18next";
import { PrimaryButton } from "../../components/ui/PrimaryButton";
import { SecondaryButton } from "../../components/ui/SecondaryButton";
import { toTravelDateTime, type TravelRide, type UpsertRideBody } from "../../api/travel";
import { formatPersonName } from "../../utils/stringUtils";

export interface RideChildChoice {
  playerProfileId: string;
  firstName?: string;
  lastName?: string;
  seatedOnThisRide: boolean;
  seatedElsewhere: boolean;
  going: boolean;
  wantsRide: boolean;
}

export const RideOfferDialog = ({
  open,
  ride,
  meetingPoint,
  meetingAt,
  showChildren,
  children,
  pending,
  serverError,
  onClose,
  onSubmit,
}: {
  open: boolean;
  ride: TravelRide | null;
  meetingPoint?: string;
  meetingAt?: string;
  showChildren: boolean;
  children: RideChildChoice[];
  pending?: boolean;
  serverError?: string | null;
  onClose: () => void;
  onSubmit: (body: UpsertRideBody) => void;
}) => {
  const { t } = useTranslation();
  const [place, setPlace] = useState("");
  const [departureAt, setDepartureAt] = useState<Dayjs | null>(null);
  const [notes, setNotes] = useState("");
  const [offered, setOffered] = useState(1);
  const [minOffered, setMinOffered] = useState(1);
  const [maxOffered, setMaxOffered] = useState(8);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    const floor = Math.max(1, ride?.seatsTaken ?? 0);
    setOffered(Math.min(8, Math.max(floor, ride?.seatsTotal ?? 1)));
    setMinOffered(floor);
    setMaxOffered(8);
    setPlace(ride?.departurePlace ?? meetingPoint ?? "");
    // dayjs(undefined) would silently mean "now"; leave the picker empty instead.
    const initialDeparture = ride?.departureAt ?? meetingAt;
    setDepartureAt(initialDeparture ? dayjs(initialDeparture) : null);
    setNotes(ride?.notes ?? "");
    setError(null);
  }, [open, ride?.id, ride?.updatedAt]);

  const ridingAlong = showChildren ? children.filter((child) => child.going && child.wantsRide && !child.seatedElsewhere) : [];
  const elsewhere = showChildren ? children.filter((child) => child.seatedElsewhere) : [];

  const submit = () => {
    if (!place.trim()) {
      setError(t("travel.form.departure-place-required"));
      return;
    }
    if (!departureAt || !departureAt.isValid()) {
      setError(t("travel.form.departure-at-required"));
      return;
    }
    if (!Number.isInteger(offered) || offered < minOffered || offered > maxOffered) {
      setError(t("travel.form.seats"));
      return;
    }
    setError(null);
    onSubmit({
      seatsTotal: offered,
      departurePlace: place.trim(),
      departureAt: toTravelDateTime(departureAt),
      notes: notes.trim() ? notes.trim() : null,
      expectedUpdatedAt: ride?.updatedAt ?? null,
    });
  };

  return (
    <Dialog open={open} onClose={pending ? undefined : onClose} fullWidth maxWidth="sm">
      <DialogTitle>{ride ? t("travel.edit-ride") : t("travel.offer-ride")}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {(error || serverError) && <Alert severity="error">{error || serverError}</Alert>}
          <Typography variant="body2">
            {showChildren ? t("travel.fixed-seat-hint") : t("travel.driver-ride-hint")}
          </Typography>
          {ridingAlong.length > 0 && (
            <Typography variant="body2" fontWeight={700}>
              {t("travel.fixed-seat", {
                names: ridingAlong.map((child) => formatPersonName(child.firstName, child.lastName)).join(", "),
              })}
            </Typography>
          )}
          {showChildren && ridingAlong.length === 0 && (
            <Typography variant="body2">{t("travel.no-fixed-child")}</Typography>
          )}
          {elsewhere.map((child) => (
            <Typography key={child.playerProfileId} variant="body2" color="text.secondary">
              {t("travel.fixed-elsewhere", { name: formatPersonName(child.firstName, child.lastName) })}
            </Typography>
          ))}
          <TextField
            label={showChildren ? t("travel.seats-for-others") : t("travel.seats-total")}
            type="number"
            value={offered}
            onChange={(change) => setOffered(Number(change.target.value))}
            slotProps={{ htmlInput: { min: minOffered, max: maxOffered } }}
            fullWidth
          />
          <TextField
            label={t("travel.departure-place")}
            value={place}
            onChange={(change) => setPlace(change.target.value)}
            required
            fullWidth
          />
          <DateTimePicker
            label={t("travel.departure-at")}
            value={departureAt}
            onChange={setDepartureAt}
            slotProps={{ textField: { fullWidth: true, required: true } }}
          />
          <TextField
            label={t("travel.notes")}
            value={notes}
            onChange={(change) => setNotes(change.target.value)}
            fullWidth
            multiline
            minRows={2}
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        <SecondaryButton onClick={onClose} disabled={pending}>{t("common.cancel")}</SecondaryButton>
        <PrimaryButton onClick={submit} disabled={pending}>{t("common.save")}</PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
