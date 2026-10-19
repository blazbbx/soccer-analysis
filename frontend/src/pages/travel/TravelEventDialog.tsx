import { useEffect, useState } from "react";
import {
  Alert,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
} from "@mui/material";
import { DateTimePicker } from "@mui/x-date-pickers/DateTimePicker";
import dayjs, { type Dayjs } from "dayjs";
import { useTranslation } from "react-i18next";
import { PrimaryButton } from "../../components/ui/PrimaryButton";
import { SecondaryButton } from "../../components/ui/SecondaryButton";
import { toTravelDateTime, type TeamEvent, type TeamEventType, type UpsertTeamEventBody } from "../../api/travel";

const blankToNull = (value: string) => {
  const trimmed = value.trim();
  return trimmed.length === 0 ? null : trimmed;
};

export const TravelEventDialog = ({
  open,
  event,
  pending,
  serverError,
  onClose,
  onSubmit,
}: {
  open: boolean;
  event: TeamEvent | null;
  pending?: boolean;
  serverError?: string | null;
  onClose: () => void;
  onSubmit: (body: UpsertTeamEventBody) => void;
}) => {
  const { t } = useTranslation();
  const [type, setType] = useState<TeamEventType>("TRAINING");
  const [startsAt, setStartsAt] = useState<Dayjs | null>(null);
  const [meetingAt, setMeetingAt] = useState<Dayjs | null>(null);
  const [deadline, setDeadline] = useState<Dayjs | null>(null);
  const [meetingPoint, setMeetingPoint] = useState("");
  const [venue, setVenue] = useState("");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    setType(event?.type ?? "TRAINING");
    setStartsAt(event?.startsAt ? dayjs(event.startsAt) : null);
    setMeetingAt(event?.meetingAt ? dayjs(event.meetingAt) : null);
    setDeadline(event?.rsvpDeadline ? dayjs(event.rsvpDeadline) : null);
    setMeetingPoint(event?.meetingPoint ?? "");
    setVenue(event?.venue ?? "");
    setNotes(event?.notes ?? "");
    setError(null);
    // Reset only when the dialog opens or another saved event is loaded.
    // A background refetch with the same updatedAt must not wipe the form.
  }, [open, event?.id, event?.updatedAt]);

  const submit = () => {
    if (!startsAt || !meetingAt || !startsAt.isValid() || !meetingAt.isValid() || (deadline && !deadline.isValid())) {
      setError(t("travel.form.times-required"));
      return;
    }
    if (!meetingPoint.trim()) {
      setError(t("travel.form.meeting-point-required"));
      return;
    }
    if (meetingAt.isAfter(startsAt)) {
      setError(t("travel.form.meeting-after-start"));
      return;
    }
    if (deadline && deadline.isAfter(startsAt)) {
      setError(t("travel.form.deadline-after-start"));
      return;
    }
    // The video / cup match links are no longer edited here. An edited event sends its stored
    // values back unchanged, so saving the form never drops them; a new event sends null.
    const video = event?.videoMatchId?.trim() || null;
    const cup = event?.cupMatchId?.trim() || null;
    setError(null);
    onSubmit({
      type,
      startsAt: toTravelDateTime(startsAt) ?? "",
      meetingAt: toTravelDateTime(meetingAt) ?? "",
      meetingPoint: meetingPoint.trim(),
      venue: blankToNull(venue),
      notes: blankToNull(notes),
      rsvpDeadline: toTravelDateTime(deadline),
      videoMatchId: video,
      cupMatchId: cup,
      expectedUpdatedAt: event?.updatedAt ?? null,
    });
  };

  return (
    <Dialog open={open} onClose={pending ? undefined : onClose} fullWidth maxWidth="sm">
      <DialogTitle>{event ? t("travel.edit-event") : t("travel.new-event")}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {(error || serverError) && <Alert severity="error">{error || serverError}</Alert>}
          <FormControl fullWidth>
            <InputLabel id="travel-event-type">{t("travel.form.type")}</InputLabel>
            <Select
              labelId="travel-event-type"
              label={t("travel.form.type")}
              value={type}
              onChange={(change) => setType(change.target.value as TeamEventType)}
            >
              <MenuItem value="MATCH">{t("travel.type.match")}</MenuItem>
              <MenuItem value="TRAINING">{t("travel.type.training")}</MenuItem>
              <MenuItem value="TOURNAMENT">{t("travel.type.tournament")}</MenuItem>
            </Select>
          </FormControl>
          <DateTimePicker label={t("travel.starts")} value={startsAt} onChange={setStartsAt} slotProps={{ textField: { fullWidth: true, required: true } }} />
          <DateTimePicker label={t("travel.meeting-at")} value={meetingAt} onChange={setMeetingAt} slotProps={{ textField: { fullWidth: true, required: true } }} />
          <TextField label={t("travel.meeting-point")} value={meetingPoint} onChange={(change) => setMeetingPoint(change.target.value)} required fullWidth />
          <TextField label={t("travel.venue")} value={venue} onChange={(change) => setVenue(change.target.value)} fullWidth />
          <DateTimePicker label={t("travel.deadline")} value={deadline} onChange={setDeadline} slotProps={{ textField: { fullWidth: true } }} />
          <TextField label={t("travel.notes")} value={notes} onChange={(change) => setNotes(change.target.value)} fullWidth multiline minRows={2} />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        <SecondaryButton onClick={onClose} disabled={pending}>{t("common.cancel")}</SecondaryButton>
        <PrimaryButton onClick={submit} disabled={pending}>{t("common.save")}</PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
