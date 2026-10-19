import { Box, Typography } from "@mui/material";
import { useTranslation } from "react-i18next";
import type { TravelPassenger, TravelRide } from "../../api/travel";
import { RideCard } from "./RideCard";
import { useTravelColors } from "./travelStyle";
import { TravelPanelHeader } from "./travelUi";

export const FreeRideList = ({
  freeRides,
  fullRides,
  freeSeats,
  bookProfilesFor,
  leaveProfilesFor,
  canEdit,
  canDelete,
  pending,
  onBook,
  onLeave,
  onEdit,
  onDelete,
  fixedPeopleFor,
}: {
  freeRides: TravelRide[];
  fullRides: TravelRide[];
  freeSeats?: number;
  bookProfilesFor: (ride: TravelRide) => TravelPassenger[];
  leaveProfilesFor: (ride: TravelRide) => TravelPassenger[];
  canEdit: (ride: TravelRide) => boolean;
  canDelete: (ride: TravelRide) => boolean;
  pending?: boolean;
  onBook: (ride: TravelRide, playerProfileId: string) => void;
  onLeave: (ride: TravelRide, playerProfileId: string) => void;
  onEdit: (ride: TravelRide) => void;
  onDelete: (ride: TravelRide) => void;
  fixedPeopleFor: (ride: TravelRide) => TravelPassenger[];
}) => {
  const { t } = useTranslation();
  const colors = useTravelColors();

  return (
    <Box
      component="section"
      sx={{
        minWidth: 0,
        minHeight: 0,
        display: "flex",
        flexDirection: "column",
        border: "1px solid",
        borderColor: colors.cardBorder,
        borderRadius: "12px",
        overflow: "hidden",
      }}
    >
      <TravelPanelHeader
        title={freeRides.length === 0 && fullRides.length > 0 ? t("travel.full-rides") : t("travel.free-rides")}
        aside={freeSeats != null && freeSeats > 0 ? t("travel.seats-open", { free: freeSeats }) : undefined}
      />
      <Box
        sx={{
          flex: 1,
          minHeight: 0,
          overflowY: { md: "auto" },
          overscrollBehavior: "contain",
          p: 1.25,
          display: "flex",
          flexDirection: "column",
          gap: 1,
        }}
      >
        {freeRides.length === 0 && fullRides.length === 0 && (
          <Typography sx={{ fontSize: 13, color: colors.muted }}>
            {t("travel.empty-free-rides")}
          </Typography>
        )}
        {freeRides.map((ride) => (
          <RideCard
            key={ride.id}
            ride={ride}
            bookProfiles={bookProfilesFor(ride)}
            leaveProfiles={leaveProfilesFor(ride)}
            canEdit={canEdit(ride)}
            canDelete={canDelete(ride)}
            pending={pending}
            onBook={(playerProfileId) => onBook(ride, playerProfileId)}
            onLeave={(playerProfileId) => onLeave(ride, playerProfileId)}
            onEdit={() => onEdit(ride)}
            onDelete={() => onDelete(ride)}
            fixedPeople={fixedPeopleFor(ride)}
          />
        ))}
        {fullRides.length > 0 && freeRides.length > 0 && (
          <Typography sx={{ mt: 0.5, fontSize: 12, fontWeight: 800, letterSpacing: 0.6, textTransform: "uppercase", color: colors.muted }}>
            {t("travel.full-rides")}
          </Typography>
        )}
        {fullRides.map((ride) => (
          <RideCard
            key={ride.id}
            ride={ride}
            bookProfiles={[]}
            leaveProfiles={leaveProfilesFor(ride)}
            canEdit={canEdit(ride)}
            canDelete={canDelete(ride)}
            pending={pending}
            onBook={() => undefined}
            onLeave={(playerProfileId) => onLeave(ride, playerProfileId)}
            onEdit={() => onEdit(ride)}
            onDelete={() => onDelete(ride)}
            fixedPeople={fixedPeopleFor(ride)}
          />
        ))}
      </Box>
    </Box>
  );
};
