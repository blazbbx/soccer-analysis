import { useEffect, useRef, useState } from "react";
import { Box, Tooltip, Typography } from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { useTranslation } from "react-i18next";
import {
  formatTravelClock,
  formatTravelDay,
  splitTravelEvents,
  travelEventTitle,
  type AttendanceStatus,
  type TeamEvent,
  type TeamEventType,
} from "../../api/travel";
import { useTravelColors } from "./travelStyle";
import { TravelPill, type TravelTone } from "./travelUi";

const typeKey = (type?: TeamEventType) => {
  if (type === "TRAINING") return "travel.type.training";
  if (type === "TOURNAMENT") return "travel.type.tournament";
  return "travel.type.match";
};

/** Answer label without a name: Jön / Nem jön / Még nem tudom / Nincs válasz. */
const answerKey = (status: AttendanceStatus) => {
  if (status === "NOT_GOING") return "travel.child-status.not-going";
  if (status === "MAYBE") return "travel.child-status.maybe";
  if (status === "NO_RESPONSE") return "travel.status.no-response";
  return "travel.child-status.going";
};

const SUMMARY_ORDER: AttendanceStatus[] = ["GOING", "NOT_GOING", "MAYBE", "NO_RESPONSE"];

const chipTone = (status: AttendanceStatus): TravelTone => {
  if (status === "GOING") return "ok";
  if (status === "NOT_GOING") return "err";
  return "warn";
};

export interface TravelStatusChip {
  profileId: string;
  name: string;
  status: AttendanceStatus;
  highlighted: boolean;
}

export const TravelEventList = ({
  events,
  selectedEventId,
  onSelect,
  statusChips,
}: {
  events: TeamEvent[];
  selectedEventId: string;
  onSelect: (eventId: string) => void;
  statusChips: (event: TeamEvent) => TravelStatusChip[];
}) => {
  const { t } = useTranslation();
  const colors = useTravelColors();
  const { upcoming, past } = splitTravelEvents(events);
  const [pastOpen, setPastOpen] = useState(false);
  const seededPast = useRef(false);
  useEffect(() => {
    if (seededPast.current || events.length === 0) return;
    seededPast.current = true;
    if (upcoming.length === 0 && past.length > 0) setPastOpen(true);
  }, [events.length, upcoming.length, past.length]);
  const dotColor = (status: AttendanceStatus) => {
    if (status === "GOING") return colors.okText;
    if (status === "NOT_GOING") return colors.errText;
    if (status === "MAYBE") return colors.amberText;
    return colors.muted;
  };

  if (events.length === 0) {
    return (
      <Typography variant="body2" sx={{ color: colors.muted }}>
        {t("travel.empty-events")}
      </Typography>
    );
  }

  const card = (event: TeamEvent) => {
        const selected = event.id === selectedEventId;
        const chips = statusChips(event);
        const title = travelEventTitle(event, t(typeKey(event.type)));
        const venue = event.venue?.trim();
        const clock = formatTravelClock(event.meetingAt);
        const place = event.meetingPoint?.trim();
        const showMeeting = Boolean(clock) && (Boolean(venue) || selected);
        const meetingLine = place && place !== title
          ? t("travel.meeting-line", { time: clock, place })
          : `${t("travel.meeting")} ${clock}`;

        return (
          <Box
            key={event.id}
            role="button"
            tabIndex={0}
            aria-pressed={selected}
            onClick={() => onSelect(event.id)}
            onKeyDown={(key) => {
              if (key.key === "Enter" || key.key === " ") {
                key.preventDefault();
                onSelect(event.id);
              }
            }}
            sx={{
              p: 1.6,
              cursor: "pointer",
              bgcolor: selected ? colors.highlight : colors.card,
              borderRadius: "14px",
              border: "1px solid",
              borderColor: selected ? colors.selected : colors.cardBorder,
              boxShadow: selected ? `0 0 0 1px ${colors.selected} inset` : "none",
              transition: "border-color .15s, background-color .15s",
              outline: "none",
              "&:hover": { borderColor: selected ? colors.selected : colors.brandBorder },
              "&:focus-visible": { borderColor: colors.selected },
            }}
          >
            <Box sx={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 1 }}>
              <Box
                sx={{
                  px: 0.9,
                  py: 0.2,
                  borderRadius: "999px",
                  bgcolor: colors.infoBg,
                  color: colors.infoText,
                  fontSize: 11.5,
                  fontWeight: 800,
                  lineHeight: 1.45,
                  flexShrink: 0,
                }}
              >
                {t(typeKey(event.type))}
              </Box>
              {chips.length === 1 && (
                <TravelPill tone={chipTone(chips[0].status)}>
                  {t(answerKey(chips[0].status))}
                </TravelPill>
              )}
              {chips.length > 1 && (
                <Tooltip
                  arrow
                  placement="left"
                  title={(
                    <Box sx={{ display: "flex", flexDirection: "column", gap: 0.25 }}>
                      {chips.map((chip) => (
                        <span key={chip.profileId}>
                          {t("travel.card-status", { name: chip.name, status: t(answerKey(chip.status)) })}
                        </span>
                      ))}
                    </Box>
                  )}
                >
                  <Box
                    aria-label={chips
                      .map((chip) => t("travel.card-status", { name: chip.name, status: t(answerKey(chip.status)) }))
                      .join(", ")}
                    sx={{ display: "flex", alignItems: "center", gap: 1, flexShrink: 0, pt: 0.25 }}
                  >
                    {SUMMARY_ORDER.map((status) => {
                      const count = chips.filter((chip) => chip.status === status).length;
                      if (count === 0) return null;
                      return (
                        <Box
                          key={status}
                          component="span"
                          sx={{ display: "inline-flex", alignItems: "center", gap: 0.5, fontSize: 12, fontWeight: 700, color: colors.muted, lineHeight: 1 }}
                        >
                          <Box component="i" sx={{ width: 7, height: 7, borderRadius: "50%", bgcolor: dotColor(status), display: "inline-block" }} />
                          {count}
                        </Box>
                      );
                    })}
                  </Box>
                </Tooltip>
              )}
            </Box>
            <Typography sx={{ mt: 1, fontWeight: 800, fontSize: 15.5, color: colors.ink, lineHeight: 1.3 }}>
              {title}
            </Typography>
            <Typography sx={{ mt: 0.4, fontSize: 12.5, color: colors.muted }}>
              {formatTravelDay(event.startsAt)}
            </Typography>
            {showMeeting && clock && (
              <Typography sx={{ mt: 0.1, fontSize: 12.5, color: colors.muted }}>
                {meetingLine}
              </Typography>
            )}
          </Box>
        );
  };

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 1.25 }}>
      {upcoming.length === 0 && past.length > 0 && (
        <Typography variant="body2" sx={{ color: colors.muted }}>
          {t("travel.no-upcoming")}
        </Typography>
      )}
      {upcoming.map(card)}
      {past.length > 0 && (
        <Box
          component="button"
          type="button"
          aria-expanded={pastOpen}
          onClick={() => setPastOpen((open) => !open)}
          sx={{
            display: "flex",
            alignItems: "center",
            gap: 0.5,
            px: 0.5,
            py: 0.75,
            border: 0,
            bgcolor: "transparent",
            color: colors.muted,
            cursor: "pointer",
            font: "inherit",
            fontSize: 13,
            fontWeight: 800,
            textAlign: "left",
          }}
        >
          <ExpandMoreIcon
            sx={{
              fontSize: 18,
              transform: pastOpen ? "rotate(180deg)" : "none",
              transition: "transform .15s",
            }}
          />
          {t("travel.past-events")}
          <Box component="span" sx={{ fontWeight: 700 }}>{past.length}</Box>
        </Box>
      )}
      {pastOpen && past.map(card)}
    </Box>
  );
};
