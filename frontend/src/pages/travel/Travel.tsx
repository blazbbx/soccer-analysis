import { useEffect, useMemo, useRef, useState } from "react";
import { Alert, Box, Button, CircularProgress, Typography } from "@mui/material";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import type { TeamResponse } from "../../api/generated/model";
import type { TeamCatalogItem } from "../../api/roleTeams";
import { useRoleTeams } from "../../api/roleTeams";
import {
  bookSeat,
  createRide,
  createTeamEvent,
  deleteRide as deleteRideRequest,
  deleteTeamEvent,
  listPastTeamEvents,
  releaseSeat,
  setAttendance,
  splitTravelEvents,
  teamEventKey,
  teamEventsKey,
  TRAVEL_PAST_PAGE_SIZE,
  travelErrorMessage,
  travelErrorStatus,
  updateRide,
  updateTeamEvent,
  firstTravelEventId,
  useTeamEvent,
  useTeamEvents,
  type AttendanceStatus,
  type TeamEvent,
  type TravelPassenger,
  type TravelRide,
  type TravelRosterEntry,
  type UpsertRideBody,
  type UpsertTeamEventBody,
} from "../../api/travel";
import { FilledActionButton } from "../../components/ui/FilledActionButton";
import type { TeamPick } from "../../components/TeamAutocomplete";
import { useAuth } from "../../context/AuthContext";
import { useChildren } from "../../context/ChildrenContext";
import { useView } from "../../context/ViewContext";
import { useSnackbar } from "../../context/SnackbarContext";
import { useChildScope } from "../../hooks/useChildScope";
import { HoldToDeleteDialog } from "../clubs/dialogs/HoldToDeleteDialog";
import { ROLES } from "../../types/roles";
import { formatPersonName } from "../../utils/stringUtils";
import { ReleaseSeatConfirmDialog } from "./ReleaseSeatConfirmDialog";
import { RideOfferDialog, type RideChildChoice } from "./RideOfferDialog";
import { TravelEventBoard } from "./TravelEventBoard";
import { TravelEventDialog } from "./TravelEventDialog";
import { TravelEventList, type TravelStatusChip } from "./TravelEventList";
import { TravelTeamBar } from "./TravelTeamBar";
import { bookProfiles, canAnswer, familyOf, isDriver, leaveProfiles, offerButtonVisible } from "./travelRules";
import { useTravelColors } from "./travelStyle";

export const Travel = () => {
  const { t } = useTranslation();
  const { user } = useAuth();
  const { activeView } = useView();
  const { showError } = useSnackbar();
  const colors = useTravelColors();
  const queryClient = useQueryClient();
  const { selected, childrenList } = useChildren();
  const isAdmin = activeView === ROLES.ADMIN;
  const managesEvents = activeView === ROLES.ADMIN || activeView === ROLES.CLUB_ADMIN || activeView === ROLES.COACH;
  const { data: catalog, isLoading: isLoadingTeams, isError: teamsFailed } = useRoleTeams();
  const { scopeTeams, active: parentView, selectedTeamIds } = useChildScope();
  const teams = scopeTeams(catalog?.items ?? []);
  const needsSearch = isAdmin || Boolean(catalog?.partial);
  const hasTeams = parentView ? selectedTeamIds.size > 0 : (catalog?.total ?? 0) > 0;

  const [selectedTeamId, setSelectedTeamId] = useState("");
  const [pickedTeam, setPickedTeam] = useState<TeamCatalogItem | null>(null);
  const [selectedEventId, setSelectedEventId] = useState("");
  const [eventDialog, setEventDialog] = useState<"create" | "edit" | null>(null);
  const [rideDialog, setRideDialog] = useState<"create" | TravelRide | null>(null);
  const [deleteEventOpen, setDeleteEventOpen] = useState(false);
  const [deleteRide, setDeleteRide] = useState<TravelRide | null>(null);
  const [releasePrompt, setReleasePrompt] = useState<{
    entry: TravelRosterEntry;
    status: "NOT_GOING" | "MAYBE" | "NO_RIDE";
  } | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [driverBlock, setDriverBlock] = useState<string | null>(null);
  // Earlier events loaded with "load older", for one team; done = the last page was short.
  const [older, setOlder] = useState<{ teamId: string; events: TeamEvent[]; done: boolean }>(
    { teamId: "", events: [], done: false });
  const [loadingOlder, setLoadingOlder] = useState(false);

  useEffect(() => {
    if (teams.length === 1 && teams[0]?.id) {
      if (selectedTeamId !== teams[0].id) setSelectedTeamId(teams[0].id);
      return;
    }
    if (needsSearch) return;
    if (teams.length === 0) {
      setSelectedTeamId("");
      return;
    }
    const stillThere = teams.some((team) => team.id === selectedTeamId);
    if (stillThere) return;
    setSelectedTeamId(teams[0].id ?? "");
  }, [teams, selectedTeamId, needsSearch]);

  useEffect(() => {
    setSelectedEventId("");
  }, [selectedTeamId]);

  useEffect(() => {
    setDriverBlock(null);
  }, [selectedEventId]);

  const eventsQuery = useTeamEvents(selectedTeamId);
  const detailQuery = useTeamEvent(selectedTeamId, selectedEventId);
  const olderEvents = older.teamId === selectedTeamId ? older.events : null;
  const events = useMemo(() => {
    const base = eventsQuery.data ?? [];
    if (!olderEvents || olderEvents.length === 0) return base;
    const ids = new Set(base.map((event) => event.id));
    return [...base, ...olderEvents.filter((event) => !ids.has(event.id))];
  }, [eventsQuery.data, olderEvents]);
  const hasMorePast = olderEvents
    ? !older.done
    : splitTravelEvents(eventsQuery.data ?? []).past.length >= TRAVEL_PAST_PAGE_SIZE;

  const loadOlder = async () => {
    const teamId = selectedTeamId;
    const { past } = splitTravelEvents(events);
    const oldest = past[past.length - 1];
    if (!teamId || !oldest || loadingOlder) return;
    setLoadingOlder(true);
    try {
      const page = await listPastTeamEvents(teamId, oldest);
      setOlder((current) => {
        const kept = current.teamId === teamId ? current.events : [];
        return { teamId, events: [...kept, ...page], done: page.length < TRAVEL_PAST_PAGE_SIZE };
      });
    } catch (error) {
      showError(travelErrorMessage(error, t("travel.load-failed")));
    } finally {
      setLoadingOlder(false);
    }
  };
  const detail = detailQuery.data;

  useEffect(() => {
    if (events.length === 0) {
      if (selectedEventId) setSelectedEventId("");
      return;
    }
    if (!events.some((event) => event.id === selectedEventId)) {
      setSelectedEventId(firstTravelEventId(events));
    }
  }, [events, selectedEventId]);

  const selectionRef = useRef({ teamId: selectedTeamId, eventId: selectedEventId });
  selectionRef.current = { teamId: selectedTeamId, eventId: selectedEventId };
  const stillHere = (teamId: string, eventId: string) =>
    selectionRef.current.teamId === teamId && selectionRef.current.eventId === eventId;

  const writeEvent = (event: TeamEvent) => {
    queryClient.setQueryData(teamEventKey(event.teamId, event.id), event);
    setOlder((current) => ({
      ...current,
      events: current.events.map((item) => (item.id === event.id ? event : item)),
    }));
    queryClient.setQueryData<TeamEvent[]>(teamEventsKey(event.teamId), (current) => {
      const items = (current ?? []).filter((item) => item.id !== event.id);
      items.push(event);
      items.sort((left, right) =>
        (left.startsAt ?? "").localeCompare(right.startsAt ?? "") || left.id.localeCompare(right.id));
      return items;
    });
    // exact: the list key is a prefix of the detail key, so a broad invalidate would refetch a deleted event.
    void queryClient.invalidateQueries({ queryKey: teamEventsKey(event.teamId), exact: true });
    setFormError(null);
  };

  const selectSavedEvent = (teamId: string, eventIdAtClick: string, savedEventId: string) => {
    if (stillHere(teamId, eventIdAtClick)) setSelectedEventId(savedEventId);
  };

  const refreshSavedDetail = async (teamId: string, eventId: string) => {
    await queryClient.invalidateQueries({ queryKey: teamEventKey(teamId, eventId) });
    await queryClient.invalidateQueries({ queryKey: teamEventsKey(teamId), exact: true });
  };

  const fail = (error: unknown, teamId?: string, eventId?: string) => {
    const message = travelErrorMessage(error, t("travel.save-failed"));
    setFormError(message);
    showError(message);
    if (teamId && eventId && travelErrorStatus(error) === 409 && stillHere(teamId, eventId)) {
      void refreshSavedDetail(teamId, eventId);
    }
  };

  const createEvent = useMutation({
    mutationFn: ({ teamId, body }: { teamId: string; eventId: string; body: UpsertTeamEventBody }) =>
      createTeamEvent(teamId, body),
    onSuccess: (event, variables) => {
      writeEvent(event);
      selectSavedEvent(variables.teamId, variables.eventId, event.id);
      setEventDialog(null);
    },
    onError: (error, variables) => fail(error, variables.teamId, variables.eventId),
  });
  const saveEvent = useMutation({
    mutationFn: ({ teamId, eventId, body }: { teamId: string; eventId: string; body: UpsertTeamEventBody }) =>
      updateTeamEvent(teamId, eventId, body),
    onSuccess: (event, variables) => {
      writeEvent(event);
      selectSavedEvent(variables.teamId, variables.eventId, event.id);
      setEventDialog(null);
    },
    onError: (error, variables) => fail(error, variables.teamId, variables.eventId),
  });
  const removeEvent = useMutation({
    mutationFn: ({ teamId, eventId }: { teamId: string; eventId: string }) =>
      deleteTeamEvent(teamId, eventId),
    onSuccess: async (_empty, { teamId, eventId }) => {
      queryClient.setQueryData<TeamEvent[]>(teamEventsKey(teamId), (current) =>
        (current ?? []).filter((item) => item.id !== eventId));
      setOlder((current) => ({ ...current, events: current.events.filter((item) => item.id !== eventId) }));
      setDeleteEventOpen(false);
      queryClient.removeQueries({ queryKey: teamEventKey(teamId, eventId) });
      if (stillHere(teamId, eventId)) setSelectedEventId("");
      await queryClient.invalidateQueries({ queryKey: teamEventsKey(teamId), exact: true });
    },
    onError: (error, variables) => fail(error, variables.teamId, variables.eventId),
  });
  const attendance = useMutation({
    mutationFn: ({
      teamId, eventId, profileId, status, wantsRide,
    }: {
      teamId: string;
      eventId: string;
      profileId: string;
      status: AttendanceStatus;
      wantsRide: boolean;
      ownDriver: boolean;
    }) => setAttendance(teamId, eventId, profileId, status, wantsRide),
    onSuccess: (event, variables) => {
      writeEvent(event);
      selectSavedEvent(variables.teamId, variables.eventId, event.id);
      setReleasePrompt(null);
      setDriverBlock(null);
    },
    onError: (error: unknown, variables) => {
      // Only the driver's own profile gets the "delete your car" action. A child's 409 must not remove the parent's car.
      if (
        variables.ownDriver
        && variables.status !== "GOING"
        && travelErrorStatus(error) === 409
        && stillHere(variables.teamId, variables.eventId)
      ) {
        setReleasePrompt(null);
        setDriverBlock(travelErrorMessage(error, t("travel.driver-must-delete-ride")));
        void refreshSavedDetail(variables.teamId, variables.eventId);
        return;
      }
      fail(error, variables.teamId, variables.eventId);
    },
  });
  const addRide = useMutation({
    mutationFn: ({ teamId, eventId, body }: { teamId: string; eventId: string; body: UpsertRideBody }) =>
      createRide(teamId, eventId, body),
    onSuccess: (event, variables) => {
      writeEvent(event);
      selectSavedEvent(variables.teamId, variables.eventId, event.id);
      setRideDialog(null);
    },
    onError: (error, variables) => fail(error, variables.teamId, variables.eventId),
  });
  const saveRide = useMutation({
    mutationFn: ({
      teamId, eventId, offerId, body,
    }: {
      teamId: string;
      eventId: string;
      offerId: string;
      body: UpsertRideBody;
    }) => updateRide(teamId, eventId, offerId, body),
    onSuccess: (event, variables) => {
      writeEvent(event);
      selectSavedEvent(variables.teamId, variables.eventId, event.id);
      setRideDialog(null);
    },
    onError: (error, variables) => fail(error, variables.teamId, variables.eventId),
  });
  const removeRide = useMutation({
    mutationFn: ({ teamId, eventId, offerId }: { teamId: string; eventId: string; offerId: string }) =>
      deleteRideRequest(teamId, eventId, offerId),
    onSuccess: async (_empty, { teamId, eventId }) => {
      setDeleteRide(null);
      await refreshSavedDetail(teamId, eventId);
    },
    onError: (error, variables) => fail(error, variables.teamId, variables.eventId),
  });
  const book = useMutation({
    mutationFn: ({
      teamId, eventId, offerId, profileId,
    }: {
      teamId: string;
      eventId: string;
      offerId: string;
      profileId: string;
    }) => bookSeat(teamId, eventId, offerId, profileId),
    onSuccess: (event, variables) => {
      writeEvent(event);
      selectSavedEvent(variables.teamId, variables.eventId, event.id);
    },
    onError: (error, variables) => fail(error, variables.teamId, variables.eventId),
  });
  const leave = useMutation({
    mutationFn: ({
      teamId, eventId, offerId, profileId,
    }: {
      teamId: string;
      eventId: string;
      offerId: string;
      profileId: string;
    }) => releaseSeat(teamId, eventId, offerId, profileId),
    onSuccess: async (_empty, { teamId, eventId }) => {
      await refreshSavedDetail(teamId, eventId);
    },
    onError: (error, variables) => fail(error, variables.teamId, variables.eventId),
  });

  const pending = createEvent.isPending || saveEvent.isPending || removeEvent.isPending
    || attendance.isPending || addRide.isPending || saveRide.isPending
    || removeRide.isPending || book.isPending || leave.isPending;

  const selectedFromList = teams.find((team) => team.id === selectedTeamId) ?? null;
  const selectedTeam = selectedFromList ?? (pickedTeam?.id === selectedTeamId ? pickedTeam : null);
  const menuTeams: TeamResponse[] = teams.map((team) => ({
    id: team.id,
    name: team.name,
    shortName: team.shortName,
    logoUrl: team.logoUrl,
    clubId: team.clubId,
    clubName: team.clubName,
  }));

  const selectTeam = (teamId: string, team?: TeamPick | null) => {
    setSelectedTeamId(teamId);
    setPickedTeam(team?.id
      ? { id: team.id, name: team.name ?? undefined, clubName: team.clubName ?? undefined }
      : null);
  };

  const callerIds = new Set((detail?.callerProfiles ?? []).map((profile) => profile.playerProfileId));
  const childIds = new Set(childrenList.map((child) => child.profileId));

  const canRespond = (entry: TravelRosterEntry) => canAnswer(entry, activeView, callerIds, childIds);
  const bookProfilesFor = (ride: TravelRide): TravelPassenger[] =>
    detail ? bookProfiles(ride, detail.roster ?? [], activeView, user?.id, callerIds, childIds) : [];
  const leaveProfilesFor = (ride: TravelRide): TravelPassenger[] =>
    leaveProfiles(ride, activeView, user?.id, callerIds, childIds);

  const isOwnLinkedProfile = (profileId: string) =>
    (detail?.callerProfiles ?? []).some((profile) => profile.playerProfileId === profileId)
    && !childIds.has(profileId);

  const sendAttendance = (profileId: string, status: AttendanceStatus, wantsRide: boolean) => {
    attendance.mutate({
      teamId: selectedTeamId,
      eventId: selectedEventId,
      profileId,
      status,
      wantsRide,
      ownDriver: isOwnLinkedProfile(profileId),
    });
  };

  const respond = (entry: TravelRosterEntry, status: AttendanceStatus) => {
    const wantsRide = status === "GOING" && entry.wantsRide === true;
    if ((status === "NOT_GOING" || status === "MAYBE") && entry.offerId) {
      setReleasePrompt({ entry, status });
      return;
    }
    sendAttendance(entry.playerProfileId, status, wantsRide);
  };

  const wantRide = (entry: TravelRosterEntry, wantsRide: boolean) => {
    if (!wantsRide && entry.offerId) {
      setReleasePrompt({ entry, status: "NO_RIDE" });
      return;
    }
    sendAttendance(entry.playerProfileId, "GOING", wantsRide);
  };

  const rideChoices = (ride: TravelRide | null): RideChildChoice[] => {
    if (!detail) return [];
    return (detail.callerProfiles ?? [])
      .filter((profile) => childIds.has(profile.playerProfileId))
      .map((profile) => {
        const row = (detail.roster ?? []).find((entry) => entry.playerProfileId === profile.playerProfileId);
        const offerId = row?.offerId;
        return {
          playerProfileId: profile.playerProfileId,
          firstName: profile.firstName,
          lastName: profile.lastName,
          going: row?.status === "GOING",
          wantsRide: row?.wantsRide === true,
          seatedOnThisRide: Boolean(ride && offerId === ride.id),
          seatedElsewhere: Boolean(offerId && (!ride || offerId !== ride.id)),
        };
      });
  };

  const myRide = (detail?.rides ?? []).find((ride) => isDriver(user?.id, ride));
  const drivingAlready = Boolean(myRide);
  const deleteRidePassengers = deleteRide ? (deleteRide.passengers?.length ?? deleteRide.seatsTaken) : 0;
  const canOffer = offerButtonVisible(detail?.callerCanOfferRide, activeView);

  const statusChips = (event: TeamEvent): TravelStatusChip[] => {
    const roster = (detail?.id === event.id ? detail.roster : undefined) ?? event.roster;
    const chipFor = (
      profileId: string,
      firstName: string | undefined,
      lastName: string | undefined,
      callerStatus: AttendanceStatus | undefined,
      highlighted: boolean,
    ): TravelStatusChip | null => {
      const row = roster?.find((entry) => entry.playerProfileId === profileId);
      const name = formatPersonName(firstName || row?.firstName, lastName || row?.lastName);
      if (!name) return null;
      return {
        profileId,
        name,
        status: callerStatus ?? row?.status ?? "NO_RESPONSE",
        highlighted,
      };
    };

    if (activeView === ROLES.PARENT) {
      const linked = (event.callerProfiles ?? []).filter((profile) => childIds.has(profile.playerProfileId));
      const sources = linked.length > 0
        ? linked
        : (roster ?? []).filter((entry) => childIds.has(entry.playerProfileId));
      return sources
        .map((profile) => chipFor(
          profile.playerProfileId,
          profile.firstName,
          profile.lastName,
          profile.status,
          profile.playerProfileId === selected?.profileId,
        ))
        .filter((chip): chip is TravelStatusChip => chip != null)
        .sort((left, right) => Number(right.highlighted) - Number(left.highlighted)
          || left.name.localeCompare(right.name, undefined, { sensitivity: "base" }));
    }

    if (activeView !== ROLES.PLAYER) return [];
    const playerProfile = event.callerProfiles?.find((profile) => !childIds.has(profile.playerProfileId));
    if (!playerProfile) return [];
    const chip = chipFor(
      playerProfile.playerProfileId,
      playerProfile.firstName,
      playerProfile.lastName,
      playerProfile.status,
      true,
    );
    return chip ? [chip] : [];
  };

  const ownChildren = (detail?.roster ?? [])
    .filter((entry) => activeView === ROLES.PARENT && childIds.has(entry.playerProfileId))
    .sort((left, right) => {
      const leftSelected = left.playerProfileId === selected?.profileId ? 0 : 1;
      const rightSelected = right.playerProfileId === selected?.profileId ? 0 : 1;
      return leftSelected - rightSelected
        || formatPersonName(left.firstName, left.lastName).localeCompare(formatPersonName(right.firstName, right.lastName), undefined, { sensitivity: "base" });
    });

  const ownPlayers: TravelRosterEntry[] = (() => {
    if (activeView !== ROLES.PLAYER || !detail) return [];
    const profiles = detail.callerProfiles ?? [];
    const own = profiles.filter((profile) => !childIds.has(profile.playerProfileId));
    return own.map((profile) => {
      const row = (detail.roster ?? []).find((entry) => entry.playerProfileId === profile.playerProfileId);
      return row ?? {
        playerProfileId: profile.playerProfileId,
        firstName: profile.firstName,
        lastName: profile.lastName,
        status: profile.status,
      };
    });
  })();
  const statusPeople = ownPlayers.length > 0 ? ownPlayers : ownChildren;

  const namedTeam = teams.find((team) => team.id === selectedTeamId) ?? (teams.length === 1 ? teams[0] : null);
  const club = namedTeam?.clubName?.trim();
  const teamRight = namedTeam?.shortName?.trim() || namedTeam?.name?.trim();
  const teamLabel = teams.length <= 1
    ? (club && teamRight && club !== teamRight ? `${club} · ${teamRight}` : (teamRight || club || ""))
    : "";

  if (isLoadingTeams) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", py: 8 }}>
        <CircularProgress sx={{ color: colors.button }} />
      </Box>
    );
  }

  if (!hasTeams) {
    return (
      <Box sx={{ minHeight: "100vh", bgcolor: colors.page, py: 4, px: 3 }}>
        <Typography sx={{ color: colors.muted }}>{t("travel.empty-teams")}</Typography>
      </Box>
    );
  }

  const editingRide = rideDialog && rideDialog !== "create" ? rideDialog : null;
  const showTeamBar = teams.length > 1 || (needsSearch && teams.length !== 1);
  // The selector sits in the left grid column in both states (with and without a selected team),
  // so it keeps the same width and position. The top padding keeps the outlined field's floating
  // label inside the box, because the grid clips its overflow on desktop.
  const gridColumns = { xs: "minmax(0, 1fr)", md: "minmax(260px, 320px) minmax(0, 1fr)" };
  const gridGap = { xs: 1.5, md: 2 };
  const teamBarBox = {
    pt: 1.25,
    mb: 1,
    width: "100%",
    flexShrink: 0,
    boxSizing: "border-box" as const,
  };
  const teamBar = (
    <TravelTeamBar
      teams={menuTeams}
      selectedTeamId={selectedTeamId}
      onSelect={selectTeam}
      searchTeams={needsSearch && teams.length !== 1}
      selectedTeam={selectedTeam}
    />
  );

  return (
    <Box
      sx={{
        height: { xs: "auto", md: "100vh" },
        minHeight: "100vh",
        maxHeight: { md: "100vh" },
        boxSizing: "border-box",
        overflow: { xs: "visible", md: "hidden" },
        display: "flex",
        flexDirection: "column",
        bgcolor: colors.page,
        py: { xs: 2, md: 2.5 },
        px: { xs: 2, md: 3 },
      }}
    >
      <Box sx={{ display: "flex", alignItems: "baseline", gap: 1.5, flexWrap: "wrap", mb: 1.5, flexShrink: 0 }}>
        <Typography sx={{ fontSize: { xs: 24, md: 28 }, fontWeight: 800, color: colors.ink, lineHeight: 1.2, letterSpacing: -0.3 }}>
          {t("sidebar.travel")}
        </Typography>
        {teamLabel && (
          <Typography sx={{ fontSize: { xs: 16, md: 19 }, fontWeight: 500, color: colors.muted }}>
            {teamLabel}
          </Typography>
        )}
      </Box>
      {!selectedTeamId && (
        <Box sx={{ display: "grid", gridTemplateColumns: gridColumns, gap: gridGap, flexShrink: 0 }}>
          <Box sx={{ minWidth: 0 }}>
            {showTeamBar && (
              <Box sx={teamBarBox}>
                {teamBar}
              </Box>
            )}
            <Typography sx={{ color: colors.muted }}>{t("travel.pick-team")}</Typography>
          </Box>
        </Box>
      )}
      {(teamsFailed || eventsQuery.isError) && (
        <Alert severity="error" sx={{ mb: 2 }}>{t("travel.load-failed")}</Alert>
      )}
      {selectedTeamId && (
        <Box
          sx={{
            flex: 1,
            minHeight: 0,
            display: "grid",
            gridTemplateColumns: gridColumns,
            gridTemplateRows: { xs: "auto", md: "minmax(0, 1fr)" },
            gap: gridGap,
            overflow: { xs: "visible", md: "hidden" },
          }}
        >
          <Box sx={{ minHeight: 0, display: "flex", flexDirection: "column" }}>
            {showTeamBar && (
              <Box sx={teamBarBox}>
                {teamBar}
              </Box>
            )}
            {managesEvents && (
              <Box sx={{ mb: 1.5, flexShrink: 0 }}>
                <FilledActionButton
                  onClick={() => { setFormError(null); setEventDialog("create"); }}
                  sx={{
                    py: 0.6,
                    px: 1.75,
                    fontSize: 14,
                    borderRadius: "10px",
                    backgroundColor: colors.button,
                    color: colors.buttonText,
                    "&:hover": { backgroundColor: colors.buttonHover },
                  }}
                >
                  {t("travel.new-event")}
                </FilledActionButton>
              </Box>
            )}
            <Box sx={{ flex: 1, minHeight: 0, overflowY: { md: "auto" }, overscrollBehavior: "contain", pr: { md: 0.5 } }}>
              {eventsQuery.isLoading ? (
                <CircularProgress size={28} sx={{ color: colors.button }} />
              ) : (
                <TravelEventList
                  key={selectedTeamId}
                  events={events}
                  selectedEventId={selectedEventId}
                  onSelect={setSelectedEventId}
                  statusChips={statusChips}
                  hasMorePast={hasMorePast}
                  loadingOlder={loadingOlder}
                  onLoadOlder={() => void loadOlder()}
                />
              )}
            </Box>
          </Box>
          <Box sx={{ height: { md: "100%" }, minHeight: 0, overflow: { md: "hidden" }, display: "flex", flexDirection: "column" }}>
            {detailQuery.isLoading && selectedEventId && <CircularProgress size={28} sx={{ color: colors.button }} />}
            {detailQuery.isError && <Alert severity="error">{t("travel.load-failed")}</Alert>}
            {detail && driverBlock && (
              <Alert
                severity="warning"
                sx={{ mb: 1.5 }}
                onClose={() => setDriverBlock(null)}
                action={myRide ? (
                  <Button
                    color="inherit"
                    size="small"
                    onClick={() => { setDriverBlock(null); setDeleteRide(myRide); }}
                  >
                    {t("travel.delete-my-ride")}
                  </Button>
                ) : undefined}
              >
                {driverBlock}
              </Alert>
            )}
            {detail && (
              <TravelEventBoard
                event={detail}
                bookProfilesFor={bookProfilesFor}
                leaveProfilesFor={leaveProfilesFor}
                canEditRide={(ride) => isDriver(user?.id, ride) && detail?.callerCanOfferRide === true}
                canDeleteRide={(ride) => isDriver(user?.id, ride)}
                canRespond={canRespond}
                pending={pending}
                canOffer={canOffer && !drivingAlready}
                canManage={managesEvents}
                onOffer={() => { setFormError(null); setRideDialog("create"); }}
                onEditEvent={() => { setFormError(null); setEventDialog("edit"); }}
                onDeleteEvent={() => setDeleteEventOpen(true)}
                onBook={(ride, profileId) => book.mutate({
                  teamId: selectedTeamId,
                  eventId: selectedEventId,
                  offerId: ride.id,
                  profileId,
                })}
                onLeave={(ride, profileId) => leave.mutate({
                  teamId: selectedTeamId,
                  eventId: selectedEventId,
                  offerId: ride.id,
                  profileId,
                })}
                onEditRide={(ride) => { setFormError(null); setRideDialog(ride); }}
                onDeleteRide={setDeleteRide}
                onRespond={respond}
                onWantRide={wantRide}
                ownChildren={statusPeople}
                selfStatus={ownPlayers.length > 0}
                highlightedChildId={ownPlayers[0]?.playerProfileId ?? selected?.profileId}
                fixedPeopleFor={familyOf}
              />
            )}
          </Box>
        </Box>
      )}

      <TravelEventDialog
        open={eventDialog != null}
        event={eventDialog === "edit" ? detail ?? null : null}
        pending={pending}
        serverError={formError}
        onClose={() => setEventDialog(null)}
        onSubmit={(body) => {
          if (eventDialog === "edit") {
            saveEvent.mutate({ teamId: selectedTeamId, eventId: selectedEventId, body });
          } else {
            createEvent.mutate({
              teamId: selectedTeamId,
              eventId: selectedEventId,
              body: { ...body, expectedUpdatedAt: null },
            });
          }
        }}
      />
      <RideOfferDialog
        open={rideDialog != null}
        ride={editingRide}
        meetingPoint={detail?.meetingPoint}
        meetingAt={detail?.meetingAt}
        showChildren={rideChoices(editingRide).length > 0}
        children={rideChoices(editingRide)}
        pending={pending}
        serverError={formError}
        onClose={() => setRideDialog(null)}
        onSubmit={(body) => {
          if (editingRide) {
            saveRide.mutate({ teamId: selectedTeamId, eventId: selectedEventId, offerId: editingRide.id, body });
          } else {
            addRide.mutate({
              teamId: selectedTeamId,
              eventId: selectedEventId,
              body: { ...body, expectedUpdatedAt: null },
            });
          }
        }}
      />
      <ReleaseSeatConfirmDialog
        open={releasePrompt != null}
        self={Boolean(releasePrompt && callerIds.has(releasePrompt.entry.playerProfileId) && !childIds.has(releasePrompt.entry.playerProfileId))}
        personName={releasePrompt ? formatPersonName(releasePrompt.entry.firstName, releasePrompt.entry.lastName) : ""}
        status={releasePrompt?.status ?? "NOT_GOING"}
        pending={pending}
        onCancel={() => setReleasePrompt(null)}
        onConfirm={() => {
          if (!releasePrompt) return;
          if (releasePrompt.status === "NO_RIDE") {
            sendAttendance(releasePrompt.entry.playerProfileId, "GOING", false);
            return;
          }
          sendAttendance(releasePrompt.entry.playerProfileId, releasePrompt.status, false);
        }}
      />
      <HoldToDeleteDialog
        open={deleteEventOpen}
        title={t("travel.delete-event")}
        itemName={detail?.meetingPoint ?? ""}
        description={t("travel.delete-event-body")}
        pending={removeEvent.isPending}
        hold
        onClose={() => setDeleteEventOpen(false)}
        onConfirm={() => removeEvent.mutate({ teamId: selectedTeamId, eventId: selectedEventId })}
      />
      <HoldToDeleteDialog
        open={deleteRide != null}
        title={t("travel.delete-ride")}
        itemName={deleteRide?.departurePlace ?? ""}
        description={deleteRidePassengers > 0
          ? `${t("travel.delete-ride-passengers", { count: deleteRidePassengers })} ${t("travel.delete-ride-body")}`
          : t("travel.delete-ride-body")}
        pending={removeRide.isPending}
        onClose={() => setDeleteRide(null)}
        onConfirm={() => {
          if (deleteRide) {
            removeRide.mutate({ teamId: selectedTeamId, eventId: selectedEventId, offerId: deleteRide.id });
          }
        }}
      />
    </Box>
  );
};
