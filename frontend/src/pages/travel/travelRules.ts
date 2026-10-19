import type { TravelPassenger, TravelRide, TravelRosterEntry } from "../../api/travel";
import { ROLES, type Role } from "../../types/roles";

/** A roster row that still needs one of the advertised seats. */
export const needsSeat = (entry: TravelRosterEntry): boolean =>
  entry.status === "GOING" && entry.wantsRide === true && !entry.offerId && entry.parentDrives !== true;

/** Coming players who asked for a ride. Players coming another way are not part of the seat bar. */
export const askingForRideCount = (roster: readonly TravelRosterEntry[]): number =>
  roster.filter((entry) => entry.status === "GOING" && entry.wantsRide === true).length;

/** Caller profiles include linked children, so the player hat must ignore those ids. The server still checks the write. */
const mayActFor = (
  view: Role | null,
  profileId: string,
  callerIds: ReadonlySet<string>,
  childIds: ReadonlySet<string>,
): boolean => {
  if (view === ROLES.ADMIN || view === ROLES.CLUB_ADMIN) return true;
  if (view === ROLES.PARENT) return childIds.has(profileId);
  if (view === ROLES.PLAYER) return callerIds.has(profileId) && !childIds.has(profileId);
  return false;
};

export const canAnswer = (
  entry: TravelRosterEntry,
  view: Role | null,
  callerIds: ReadonlySet<string>,
  childIds: ReadonlySet<string>,
): boolean => mayActFor(view, entry.playerProfileId, callerIds, childIds);

export const offerButtonVisible = (callerCanOfferRide: boolean | undefined, view: Role | null): boolean =>
  Boolean(callerCanOfferRide) && (
    view === ROLES.COACH
    || view === ROLES.CLUB_ADMIN
    || view === ROLES.PARENT
    || view === ROLES.PLAYER
  );

export const familyOf = (ride: TravelRide): TravelPassenger[] => ride.family ?? [];

/** Advertised seats only. A child listed on the parent's car is not one of them. */
export const openSeats = (ride: TravelRide): number => Math.max(0, ride.seatsTotal - ride.seatsTaken);

export const isDriver = (userId: string | undefined, ride: TravelRide): boolean =>
  Boolean(userId && ride.driverUserId === userId);

const hidesBooking = (view: Role | null) => view === ROLES.COACH;

const passenger = (entry: TravelRosterEntry): TravelPassenger => ({
  playerProfileId: entry.playerProfileId,
  firstName: entry.firstName,
  lastName: entry.lastName,
});

export const bookProfiles = (
  ride: TravelRide,
  roster: TravelRosterEntry[],
  view: Role | null,
  userId: string | undefined,
  callerIds: ReadonlySet<string>,
  childIds: ReadonlySet<string>,
): TravelPassenger[] => {
  if (hidesBooking(view)) return [];
  const openGoing = roster.filter(needsSeat);
  const rows = openGoing.filter((entry) => mayActFor(view, entry.playerProfileId, callerIds, childIds));
  const ownRide = isDriver(userId, ride);
  return rows
    .filter((entry) => {
      const ownChild = view === ROLES.PARENT && childIds.has(entry.playerProfileId);
      return !(ownRide && callerIds.has(entry.playerProfileId) && !ownChild);
    })
    .map(passenger);
};

export const leaveProfiles = (
  ride: TravelRide,
  view: Role | null,
  userId: string | undefined,
  callerIds: ReadonlySet<string>,
  childIds: ReadonlySet<string>,
): TravelPassenger[] => {
  if (hidesBooking(view)) return [];
  const familyIds = new Set(familyOf(ride).map((person) => person.playerProfileId));
  const passengers = (ride.passengers ?? []).filter((person) => !familyIds.has(person.playerProfileId));
  const ownChildOnOwnRide = (person: TravelPassenger) =>
    isDriver(userId, ride) && childIds.has(person.playerProfileId);
  return passengers.filter((person) =>
    !ownChildOnOwnRide(person) && mayActFor(view, person.playerProfileId, callerIds, childIds));
};
