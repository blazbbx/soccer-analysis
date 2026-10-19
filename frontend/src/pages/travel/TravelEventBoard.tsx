import type { ReactNode } from "react";
import { Box, Button, Tooltip, Typography } from "@mui/material";
import DirectionsCarOutlinedIcon from "@mui/icons-material/DirectionsCarOutlined";
import EditOutlinedIcon from "@mui/icons-material/EditOutlined";
import DeleteOutlineRoundedIcon from "@mui/icons-material/DeleteOutlineRounded";
import ScheduleRoundedIcon from "@mui/icons-material/ScheduleRounded";
import WarningAmberRoundedIcon from "@mui/icons-material/WarningAmberRounded";
import PersonOutlineRoundedIcon from "@mui/icons-material/PersonOutlineRounded";
import { useTranslation } from "react-i18next";
import type { AttendanceStatus, TeamEvent, TravelPassenger, TravelRide, TravelRosterEntry } from "../../api/travel";
import { formatTravelClock, formatTravelDay, travelEventTitle } from "../../api/travel";
import { ChildStatusCard } from "./AttendanceRow";
import { FreeRideList } from "./FreeRideList";
import { OpenPeopleList } from "./OpenPeopleList";
import { askingForRideCount, needsSeat, openSeats } from "./travelRules";
import { useTravelColors } from "./travelStyle";
import { TravelPill } from "./travelUi";
import { formatPersonName } from "../../utils/stringUtils";

const typeKey = (event: TeamEvent) => {
  if (event.type === "TRAINING") return "travel.type.training";
  if (event.type === "TOURNAMENT") return "travel.type.tournament";
  return "travel.type.match";
};

export const TravelEventBoard = ({
  event,
  bookProfilesFor,
  leaveProfilesFor,
  canEditRide,
  canDeleteRide,
  canRespond,
  pending,
  canOffer,
  canManage,
  onOffer,
  onEditEvent,
  onDeleteEvent,
  onBook,
  onLeave,
  onEditRide,
  onDeleteRide,
  onRespond,
  onWantRide,
  ownChildren,
  selfStatus,
  highlightedChildId,
  fixedPeopleFor,
}: {
  event: TeamEvent;
  bookProfilesFor: (ride: TravelRide) => TravelPassenger[];
  leaveProfilesFor: (ride: TravelRide) => TravelPassenger[];
  canEditRide: (ride: TravelRide) => boolean;
  canDeleteRide: (ride: TravelRide) => boolean;
  canRespond: (entry: TravelRosterEntry) => boolean;
  pending?: boolean;
  canOffer: boolean;
  canManage: boolean;
  onOffer: () => void;
  onEditEvent: () => void;
  onDeleteEvent: () => void;
  onBook: (ride: TravelRide, playerProfileId: string) => void;
  onLeave: (ride: TravelRide, playerProfileId: string) => void;
  onEditRide: (ride: TravelRide) => void;
  onDeleteRide: (ride: TravelRide) => void;
  onRespond: (entry: TravelRosterEntry, status: AttendanceStatus) => void;
  onWantRide: (entry: TravelRosterEntry, wantsRide: boolean) => void;
  ownChildren: TravelRosterEntry[];
  selfStatus?: boolean;
  highlightedChildId?: string;
  fixedPeopleFor: (ride: TravelRide) => TravelPassenger[];
}) => {
  const { t } = useTranslation();
  const colors = useTravelColors();
  const summary = event.summary;
  const rides = event.rides ?? [];
  const freeRides = rides.filter((ride) => openSeats(ride) > 0);
  const fullRides = rides.filter((ride) => openSeats(ride) === 0);
  const meetingClock = formatTravelClock(event.meetingAt);
  const title = travelEventTitle(event, t(typeKey(event)));
  const subtitle = [
    formatTravelDay(event.startsAt),
    meetingClock ? `${t("travel.meeting")} ${meetingClock}` : "",
  ].filter((part) => part.length > 0).join(" · ");
  const place = event.meetingPoint?.trim();
  const coveredIds = new Set(ownChildren.map((entry) => entry.playerProfileId));
  const respondOutside = (entry: TravelRosterEntry) => canRespond(entry) && !coveredIds.has(entry.playerProfileId);
  const seatedOn = new Map<string, string>();
  for (const ride of rides) {
    for (const passenger of ride.passengers ?? []) {
      if (!seatedOn.has(passenger.playerProfileId)) seatedOn.set(passenger.playerProfileId, ride.id);
    }
  }
  const roster = (event.roster ?? []).map((entry) =>
    entry.offerId || !seatedOn.has(entry.playerProfileId)
      ? entry
      : { ...entry, offerId: seatedOn.get(entry.playerProfileId) });
  const claimedChildIds = rides.flatMap((ride) => fixedPeopleFor(ride).map((person) => person.playerProfileId));

  const multiple = ownChildren.length > 1;
  const promptKind = selfStatus ? "travel.self-prompt" : "travel.child-prompt";
  const promptKey = event.type === "TRAINING"
    ? `${promptKind}.training`
    : event.type === "TOURNAMENT"
      ? `${promptKind}.tournament`
      : `${promptKind}.match`;
  const childCounts = [
    { key: "go", label: "travel.count-going", color: colors.okText, count: ownChildren.filter((entry) => entry.status === "GOING").length },
    { key: "no", label: "travel.count-not-going", color: colors.errText, count: ownChildren.filter((entry) => entry.status === "NOT_GOING").length },
    { key: "maybe", label: "travel.count-maybe", color: colors.amberText, count: ownChildren.filter((entry) => entry.status === "MAYBE").length },
    { key: "none", label: "travel.count-no-response", color: colors.muted, count: ownChildren.filter((entry) => !entry.status || entry.status === "NO_RESPONSE").length },
  ].filter((item) => item.count > 0);
  const freeSeatCount = freeRides.reduce((sum, ride) => sum + openSeats(ride), 0);
  const rideRequests = askingForRideCount(roster);
  const seatShare = summary
    ? rideRequests > 0
      ? Math.min(100, (summary.seatsTotal / rideRequests) * 100)
      : summary.seatsTotal > 0 ? 100 : 0
    : 0;
  const headerButton = {
    flexShrink: 0,
    height: 32,
    borderRadius: "10px",
    textTransform: "none" as const,
    fontWeight: 700,
    fontSize: 13,
    px: 1.25,
    whiteSpace: "nowrap" as const,
    "& .MuiButton-startIcon": { mr: 0.6 },
    "& .MuiSvgIcon-root": { fontSize: 17 },
  };

  const stat = (count: number, label: string) => (
    <Box
      sx={{
        flex: 1,
        minWidth: 0,
        py: 0.75,
        px: 1,
        display: "flex",
        alignItems: "baseline",
        justifyContent: "center",
        gap: 0.75,
        bgcolor: colors.sub,
        borderRadius: "10px",
        whiteSpace: "nowrap",
        overflow: "hidden",
      }}
    >
      <Typography component="span" sx={{ fontSize: 17, fontWeight: 800, lineHeight: 1.2, color: colors.ink }}>
        {count}
      </Typography>
      <Typography component="span" noWrap sx={{ fontSize: 12, color: colors.muted, lineHeight: 1.2 }}>
        {label}
      </Typography>
    </Box>
  );

  const metaLine = (content: ReactNode, title?: string) => (
    <Typography
      component="div"
      title={title}
      sx={{
        mt: 0.25,
        fontSize: 13,
        lineHeight: 1.45,
        color: colors.muted,
        display: "flex",
        alignItems: "center",
        flexWrap: { xs: "wrap", sm: "nowrap" },
        columnGap: 0.75,
        minWidth: 0,
        whiteSpace: { xs: "normal", sm: "nowrap" },
        overflow: "hidden",
        textOverflow: "ellipsis",
      }}
    >
      {content}
    </Typography>
  );

  const firstLine = [subtitle, place && place !== title ? place : ""].filter((part) => part.length > 0).join(" · ");

  return (
    <Box
      sx={{
        flex: 1,
        minHeight: 0,
        display: "flex",
        flexDirection: "column",
        overflowX: "hidden",
        overflowY: { xs: "visible", md: "auto" },
        bgcolor: colors.card,
        border: "1px solid",
        borderColor: colors.cardBorder,
        borderRadius: "16px",
        p: { xs: 1.75, sm: 2.25 },
      }}
    >
      <Box sx={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: { xs: 1.5, sm: 2 }, flexShrink: 0, flexDirection: { xs: "column", sm: "row" } }}>
        <Box sx={{ minWidth: 0, flex: 1, width: { xs: "100%", sm: "auto" } }}>
          <Typography sx={{ fontSize: { xs: 18, sm: 20 }, fontWeight: 800, color: colors.ink, lineHeight: 1.25 }}>
            {title}
          </Typography>
          {firstLine && metaLine(firstLine, firstLine)}
          {event.rsvpDeadline && metaLine(
            <>
              <span>{t("travel.deadline")}:</span>
              <TravelPill tone="warn" icon={<ScheduleRoundedIcon />}>{formatTravelDay(event.rsvpDeadline)}</TravelPill>
            </>,
          )}
          {event.notes && metaLine(`${t("travel.notes")}: ${event.notes}`, event.notes)}
        </Box>
        {(canOffer || canManage) && (
          <Box sx={{ display: "flex", flexDirection: "column", alignItems: { xs: "flex-start", sm: "flex-end" }, gap: 1, flexShrink: 0 }}>
            {canOffer && (
              <Button
                variant="outlined"
                onClick={onOffer}
                startIcon={<DirectionsCarOutlinedIcon />}
                sx={{
                  ...headerButton,
                  height: 36,
                  px: 1.75,
                  fontSize: 14,
                  color: colors.pillText,
                  borderColor: colors.brandBorder,
                  bgcolor: colors.highlight,
                  "&:hover": { borderColor: colors.button, bgcolor: colors.pillBg },
                }}
              >
                {t("travel.offer-ride")}
              </Button>
            )}
            {canManage && (
              <Box sx={{ display: "flex", gap: 1 }}>
                <Tooltip title={t("travel.edit-event")} arrow>
                  <Button
                    variant="outlined"
                    onClick={onEditEvent}
                    startIcon={<EditOutlinedIcon />}
                    aria-label={t("travel.edit-event")}
                    sx={{
                      ...headerButton,
                      color: colors.ink,
                      borderColor: colors.cardBorder,
                      "&:hover": { borderColor: colors.brandBorder, bgcolor: colors.highlight },
                    }}
                  >
                    {t("common.edit")}
                  </Button>
                </Tooltip>
                <Tooltip title={t("travel.delete-event")} arrow>
                  <Button
                    variant="outlined"
                    onClick={onDeleteEvent}
                    startIcon={<DeleteOutlineRoundedIcon />}
                    aria-label={t("travel.delete-event")}
                    sx={{
                      ...headerButton,
                      color: colors.errText,
                      borderColor: colors.errBorder,
                      "&:hover": { borderColor: colors.errText, bgcolor: colors.errBg },
                    }}
                  >
                    {t("common.delete")}
                  </Button>
                </Tooltip>
              </Box>
            )}
          </Box>
        )}
      </Box>

      {ownChildren.length > 0 && (
        <Box
          sx={{
            mt: 1.5,
            flexShrink: 0,
            border: "1px solid",
            borderColor: colors.cardBorder,
            borderRadius: "12px",
            bgcolor: colors.sub,
            overflow: "hidden",
            containerType: "inline-size",
            containerName: "travel-kids",
          }}
        >
          <Box
            sx={{
              display: "flex",
              alignItems: "baseline",
              flexWrap: "wrap",
              columnGap: 1.25,
              rowGap: 0.25,
              px: 1.75,
              py: 0.9,
              borderBottom: "1px solid",
              borderColor: colors.cardBorder,
            }}
          >
            <Typography component="span" sx={{ fontSize: 14, fontWeight: 800, color: colors.ink }}>
              {multiple
                ? t("travel.my-children")
                : t("travel.child-status-title", { name: formatPersonName(ownChildren[0].firstName, ownChildren[0].lastName) })}
            </Typography>
            <Typography component="span" sx={{ fontSize: 12.5, color: colors.muted }}>
              {t(promptKey)}
            </Typography>
            {multiple && childCounts.length > 0 && (
              <Box sx={{ ml: "auto", display: "flex", alignItems: "center", gap: 1.25, fontSize: 12, color: colors.muted }}>
                {childCounts.map((item) => (
                  <Box key={item.key} component="span" sx={{ display: "inline-flex", alignItems: "center", gap: 0.6, whiteSpace: "nowrap" }}>
                    <Box component="i" sx={{ width: 7, height: 7, borderRadius: "50%", bgcolor: item.color, display: "inline-block" }} />
                    {t(item.label, { count: item.count })}
                  </Box>
                ))}
              </Box>
            )}
          </Box>
          <Box
            sx={{
              maxHeight: { md: 196 },
              overflowY: { md: "auto" },
              overscrollBehavior: "contain",
              "@media (min-width: 900px) and (max-height: 820px)": { maxHeight: 150 },
            }}
          >
            {ownChildren.map((entry) => {
              const seated = claimedChildIds.includes(entry.playerProfileId) || Boolean(entry.offerId);
              const note = seated && entry.status === "GOING"
                ? "seated" as const
                : needsSeat(entry)
                  ? "request" as const
                  : !entry.status || entry.status === "NO_RESPONSE"
                    ? "none" as const
                    : null;
              return (
                <ChildStatusCard
                  key={entry.playerProfileId}
                  entry={entry}
                  pending={pending}
                  highlighted={multiple && entry.playerProfileId === highlightedChildId}
                  note={note}
                  selfStatus={selfStatus}
                  onRespond={(status) => onRespond(entry, status)}
                  onWantRide={(wantsRide) => onWantRide(entry, wantsRide)}
                />
              );
            })}
          </Box>
        </Box>
      )}

      {summary && (
        <Box sx={{ flexShrink: 0 }}>
          <Box sx={{ mt: 1.5, display: "flex", gap: 1 }}>
            {stat(summary.goingCount, t("travel.status.going"))}
            {stat(summary.notGoingCount, t("travel.status.not-going"))}
            {stat(summary.maybeCount, t("travel.status.maybe"))}
            {stat(summary.noResponseCount, t("travel.status.no-response"))}
          </Box>
          <Tooltip title={t("travel.seat-capacity-hint")} placement="top-start" enterDelay={400}>
            <Box
              sx={{
                mt: 1.25,
                px: 1.5,
                py: 1.1,
                border: "1px solid",
                borderColor: colors.cardBorder,
                borderRadius: "12px",
                display: "flex",
                flexDirection: "column",
                gap: 0.75,
              }}
            >
              <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 1 }}>
                <Box sx={{ display: "inline-flex", alignItems: "center", gap: 0.75, color: colors.ink }}>
                  <DirectionsCarOutlinedIcon sx={{ fontSize: 17, color: colors.muted }} />
                  <Typography component="span" sx={{ fontSize: 13.5, fontWeight: 800 }}>
                    {t("travel.seat-capacity")}
                  </Typography>
                </Box>
                <Typography component="span" noWrap sx={{ fontSize: 13, color: colors.muted }}>
                  <Box component="b" sx={{ color: colors.ink }}>{t("travel.seat-capacity-seats", { count: summary.seatsTotal })}</Box>
                  {" · "}
                  {t("travel.seat-capacity-requests", { count: rideRequests })}
                </Typography>
              </Box>
              <Box sx={{ height: 8, borderRadius: "999px", bgcolor: colors.grayBg, overflow: "hidden" }}>
                <Box
                  sx={{
                    height: "100%",
                    width: `${seatShare}%`,
                    borderRadius: "999px",
                    background: `linear-gradient(90deg, ${colors.buttonHover}, ${colors.button})`,
                  }}
                />
              </Box>
              {(summary.capacityGap > 0 || summary.unseatedGoingCount > 0) && (
                <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", columnGap: 1.5, rowGap: 0.5, fontSize: 12.5 }}>
                  {summary.capacityGap > 0 ? (
                    <Box component="span" sx={{ display: "inline-flex", alignItems: "center", gap: 0.6, color: colors.amberText, fontWeight: 600 }}>
                      <WarningAmberRoundedIcon sx={{ fontSize: 16 }} />
                      {t("travel.seat-capacity-gap", { count: summary.capacityGap })}
                    </Box>
                  ) : <span />}
                  {summary.unseatedGoingCount > 0 && (
                    <Box component="span" sx={{ display: "inline-flex", alignItems: "center", gap: 0.6, color: colors.muted }}>
                      <PersonOutlineRoundedIcon sx={{ fontSize: 16 }} />
                      {t("travel.seat-capacity-unseated", { count: summary.unseatedGoingCount })}
                    </Box>
                  )}
                </Box>
              )}
            </Box>
          </Tooltip>
        </Box>
      )}

      <Box
        sx={{
          mt: 1.5,
          flex: { md: 1 },
          minHeight: { md: 200 },
          display: "grid",
          gridTemplateColumns: { xs: "minmax(0, 1fr)", sm: "repeat(2, minmax(0, 1fr))" },
          gridTemplateRows: { md: "minmax(0, 1fr)" },
          gap: 1.5,
        }}
      >
        <FreeRideList
          freeRides={freeRides}
          fullRides={fullRides}
          freeSeats={freeSeatCount}
          bookProfilesFor={bookProfilesFor}
          leaveProfilesFor={leaveProfilesFor}
          canEdit={canEditRide}
          canDelete={canDeleteRide}
          pending={pending}
          onBook={onBook}
          onLeave={onLeave}
          onEdit={onEditRide}
          onDelete={onDeleteRide}
          fixedPeopleFor={fixedPeopleFor}
        />
        <OpenPeopleList
          roster={roster}
          skipRequestingIds={claimedChildIds}
          canRespond={respondOutside}
          pending={pending}
          highlightedId={highlightedChildId}
          onRespond={onRespond}
        />
      </Box>
    </Box>
  );
};
