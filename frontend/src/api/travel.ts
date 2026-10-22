import { useQuery } from "@tanstack/react-query";
import type { AxiosError } from "axios";
import dayjs from "dayjs";
import i18n from "../i18n";
import { customInstance } from "./axiosInstance";

export type TeamEventType = "MATCH" | "TRAINING" | "TOURNAMENT";
export type AttendanceStatus = "GOING" | "NOT_GOING" | "MAYBE" | "NO_RESPONSE";

export interface TravelSummary {
  goingCount: number;
  notGoingCount: number;
  maybeCount: number;
  noResponseCount: number;
  seatsTotal: number;
  capacityGap: number;
  unseatedGoingCount: number;
}

export interface TravelCallerProfile {
  playerProfileId: string;
  firstName?: string;
  lastName?: string;
  status?: AttendanceStatus;
}

export interface TravelPassenger {
  playerProfileId: string;
  firstName?: string;
  lastName?: string;
}

export interface TravelRide {
  id: string;
  driverUserId?: string;
  firstName?: string;
  lastName?: string;
  seatsTotal: number;
  seatsTaken: number;
  departurePlace?: string;
  departureAt?: string;
  updatedAt?: string;
  notes?: string;
  passengers?: TravelPassenger[];
  family?: TravelPassenger[];
}

export interface TravelRosterEntry {
  playerProfileId: string;
  firstName?: string;
  lastName?: string;
  status?: AttendanceStatus;
  wantsRide?: boolean;
  respondedAt?: string;
  comment?: string;
  offerId?: string;
  parentDrives?: boolean;
}

export interface TeamEvent {
  id: string;
  teamId: string;
  type?: TeamEventType;
  startsAt?: string;
  meetingAt?: string;
  venue?: string;
  meetingPoint?: string;
  notes?: string;
  rsvpDeadline?: string;
  videoMatchId?: string;
  cupMatchId?: string;
  updatedAt?: string;
  summary?: TravelSummary;
  callerCanOfferRide?: boolean;
  callerProfiles?: TravelCallerProfile[];
  rides?: TravelRide[];
  roster?: TravelRosterEntry[];
}

export interface UpsertTeamEventBody {
  type: TeamEventType;
  startsAt: string;
  meetingAt: string;
  venue?: string | null;
  meetingPoint: string;
  notes?: string | null;
  rsvpDeadline?: string | null;
  videoMatchId?: string | null;
  cupMatchId?: string | null;
  expectedUpdatedAt?: string | null;
}

export interface UpsertRideBody {
  seatsTotal: number;
  departurePlace?: string | null;
  departureAt?: string | null;
  notes?: string | null;
  expectedUpdatedAt?: string | null;
}

export const teamEventsKey = (teamId: string) => ["/api/teams", teamId, "events"] as const;
export const teamEventKey = (teamId: string, eventId: string) =>
  ["/api/teams", teamId, "events", eventId] as const;

const eventsUrl = (teamId: string) => `/api/teams/${teamId}/events`;
const eventUrl = (teamId: string, eventId: string) => `${eventsUrl(teamId)}/${eventId}`;

const json = (body: unknown) => ({
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

export const listTeamEvents = (teamId: string) =>
  customInstance<TeamEvent[]>(eventsUrl(teamId), { method: "GET" });

/** Earlier events per page; the event list already holds the newest page of them. */
export const TRAVEL_PAST_PAGE_SIZE = 20;

/** Earlier events, newest first, older than `before` (the oldest one the client has). */
export const listPastTeamEvents = (teamId: string, before: Pick<TeamEvent, "id" | "startsAt">) =>
  customInstance<TeamEvent[]>(`${eventsUrl(teamId)}/past`, {
    method: "GET",
    params: { beforeStartsAt: before.startsAt, beforeId: before.id, limit: TRAVEL_PAST_PAGE_SIZE },
  });

export const getTeamEvent = (teamId: string, eventId: string) =>
  customInstance<TeamEvent>(eventUrl(teamId, eventId), { method: "GET" });

export const createTeamEvent = (teamId: string, body: UpsertTeamEventBody) =>
  customInstance<TeamEvent>(eventsUrl(teamId), { method: "POST", ...json(body) });

export const updateTeamEvent = (teamId: string, eventId: string, body: UpsertTeamEventBody) =>
  customInstance<TeamEvent>(eventUrl(teamId, eventId), { method: "PUT", ...json(body) });

export const deleteTeamEvent = (teamId: string, eventId: string) =>
  customInstance<void>(eventUrl(teamId, eventId), { method: "DELETE" });

export const setAttendance = (
  teamId: string,
  eventId: string,
  playerProfileId: string,
  status: AttendanceStatus,
  wantsRide = false,
) =>
  customInstance<TeamEvent>(`${eventUrl(teamId, eventId)}/attendance/${playerProfileId}`, {
    method: "PUT",
    ...json({ status, comment: null, wantsRide }),
  });

export const createRide = (teamId: string, eventId: string, body: UpsertRideBody) =>
  customInstance<TeamEvent>(`${eventUrl(teamId, eventId)}/rides`, { method: "POST", ...json(body) });

export const updateRide = (teamId: string, eventId: string, offerId: string, body: UpsertRideBody) =>
  customInstance<TeamEvent>(`${eventUrl(teamId, eventId)}/rides/${offerId}`, { method: "PUT", ...json(body) });

export const deleteRide = (teamId: string, eventId: string, offerId: string) =>
  customInstance<void>(`${eventUrl(teamId, eventId)}/rides/${offerId}`, { method: "DELETE" });

export const bookSeat = (teamId: string, eventId: string, offerId: string, playerProfileId: string) =>
  customInstance<TeamEvent>(`${eventUrl(teamId, eventId)}/rides/${offerId}/seats`, {
    method: "POST",
    ...json({ playerProfileId }),
  });

export const releaseSeat = (teamId: string, eventId: string, offerId: string, playerProfileId: string) =>
  customInstance<void>(`${eventUrl(teamId, eventId)}/rides/${offerId}/seats/${playerProfileId}`, {
    method: "DELETE",
  });

export const useTeamEvents = (teamId: string) =>
  useQuery({
    queryKey: teamEventsKey(teamId),
    queryFn: () => listTeamEvents(teamId),
    enabled: teamId.length > 0,
  });

export const useTeamEvent = (teamId: string, eventId: string) =>
  useQuery({
    queryKey: teamEventKey(teamId, eventId),
    queryFn: () => getTeamEvent(teamId, eventId),
    enabled: teamId.length > 0 && eventId.length > 0,
  });

export const travelErrorMessage = (error: unknown, fallback: string) => {
  const detail = (error as AxiosError<{ detail?: string }>).response?.data?.detail;
  return detail || fallback;
};

export const travelErrorStatus = (error: unknown) =>
  (error as AxiosError | undefined)?.response?.status;

const HU_WEEKDAYS = ["vasárnap", "hétfő", "kedd", "szerda", "csütörtök", "péntek", "szombat"];
const EN_WEEKDAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
const HU_MONTHS = ["jan.", "feb.", "márc.", "ápr.", "máj.", "jún.", "júl.", "aug.", "szept.", "okt.", "nov.", "dec."];
const EN_MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

const parsedTravel = (value?: string | null) => {
  if (!value) return null;
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed : null;
};

export const formatTravelWhen = (value?: string | null) => {
  const parsed = parsedTravel(value);
  return parsed ? parsed.format("YYYY-MM-DD HH:mm") : "";
};

export const formatTravelClock = (value?: string | null) => {
  const parsed = parsedTravel(value);
  return parsed ? parsed.format("H:mm") : "";
};

export const formatTravelDay = (value?: string | null) => {
  const parsed = parsedTravel(value);
  if (!parsed) return "";
  const hungarian = i18n.language.toLowerCase().startsWith("hu");
  const weekday = (hungarian ? HU_WEEKDAYS : EN_WEEKDAYS)[parsed.day()];
  const month = (hungarian ? HU_MONTHS : EN_MONTHS)[parsed.month()];
  const dayLabel = hungarian
    ? `${weekday.charAt(0).toUpperCase()}${weekday.slice(1)}, ${month} ${parsed.date()}.`
    : `${weekday}, ${month} ${parsed.date()}`;
  return `${dayLabel} · ${parsed.format("H:mm")}`;
};

export const travelEventTitle = (
  event: { venue?: string | null; meetingPoint?: string | null },
  fallback: string,
) => {
  const venue = event.venue?.trim();
  if (venue) return venue;
  const point = event.meetingPoint?.trim();
  if (point) return point;
  return fallback;
};

const startMillis = (event: TeamEvent) => parsedTravel(event.startsAt)?.valueOf();

const byStart = (direction: 1 | -1) => (left: TeamEvent, right: TeamEvent) => {
  const leftAt = startMillis(left);
  const rightAt = startMillis(right);
  if (leftAt == null || rightAt == null) {
    if (leftAt == null && rightAt == null) return left.id.localeCompare(right.id);
    return leftAt == null ? 1 : -1;
  }
  if (leftAt !== rightAt) return (leftAt - rightAt) * direction;
  return left.id.localeCompare(right.id) * direction;
};

/** Today and later stay in the open list. Earlier days are the archive, newest first. */
export const splitTravelEvents = (events: readonly TeamEvent[], now = dayjs()) => {
  const startOfToday = now.startOf("day").valueOf();
  const upcoming: TeamEvent[] = [];
  const past: TeamEvent[] = [];
  for (const event of events) {
    const starts = parsedTravel(event.startsAt);
    if (starts == null || starts.valueOf() >= startOfToday) upcoming.push(event);
    else past.push(event);
  }
  upcoming.sort(byStart(1));
  past.sort(byStart(-1));
  return { upcoming, past };
};

export const firstTravelEventId = (events: readonly TeamEvent[], now = dayjs()) => {
  const { upcoming, past } = splitTravelEvents(events, now);
  return upcoming[0]?.id ?? past[0]?.id ?? "";
};

export const toTravelDateTime = (value: dayjs.Dayjs | null) =>
  value && value.isValid() ? value.format("YYYY-MM-DDTHH:mm:ss") : null;
