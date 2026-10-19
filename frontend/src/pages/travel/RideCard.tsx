import { Box, Button, Typography } from "@mui/material";
import DirectionsCarOutlinedIcon from "@mui/icons-material/DirectionsCarOutlined";
import EditOutlinedIcon from "@mui/icons-material/EditOutlined";
import DeleteOutlineRoundedIcon from "@mui/icons-material/DeleteOutlineRounded";
import LogoutRoundedIcon from "@mui/icons-material/LogoutRounded";
import { useTranslation } from "react-i18next";
import { formatTravelClock, type TravelPassenger, type TravelRide } from "../../api/travel";
import { formatPersonName } from "../../utils/stringUtils";
import { openSeats } from "./travelRules";
import { useTravelColors } from "./travelStyle";
import { TravelPill } from "./travelUi";

const SeatDots = ({ total, taken, free, occupied }: { total: number; taken: number; free: string; occupied: string }) => (
  <Box sx={{ display: "flex", gap: 0.4, flexShrink: 0 }} aria-hidden>
    {Array.from({ length: total }, (_, index) => {
      const filled = index < taken;
      return (
        <Box
          key={index}
          sx={{
            width: 8,
            height: 8,
            borderRadius: "50%",
            bgcolor: filled ? occupied : free,
          }}
        />
      );
    })}
  </Box>
);

export const RideCard = ({
  ride,
  bookProfiles,
  leaveProfiles,
  canEdit,
  canDelete,
  pending,
  onBook,
  onLeave,
  onEdit,
  onDelete,
  fixedPeople,
}: {
  ride: TravelRide;
  bookProfiles: TravelPassenger[];
  leaveProfiles: TravelPassenger[];
  canEdit: boolean;
  canDelete: boolean;
  pending?: boolean;
  onBook: (playerProfileId: string) => void;
  onLeave: (playerProfileId: string) => void;
  onEdit: () => void;
  onDelete: () => void;
  fixedPeople?: TravelPassenger[];
}) => {
  const { t } = useTranslation();
  const colors = useTravelColors();
  const departs = formatTravelClock(ride.departureAt);
  const passengers = ride.passengers ?? [];
  const fixedList = fixedPeople ?? [];
  const fixedIds = new Set(fixedList.map((person) => person.playerProfileId));
  const listed = passengers.filter((passenger) => !fixedIds.has(passenger.playerProfileId));
  const free = openSeats(ride);
  const full = free === 0 && ride.seatsTotal > 0;
  const named = listed.map((passenger) => formatPersonName(passenger.firstName, passenger.lastName));
  const sittingNames = listed.length <= 3
    ? named.join(", ")
    : t("travel.sitting-more", { name: named[0], count: listed.length - 1 });
  const bits = [
    free > 0 ? t("travel.seats-open", { free }) : "",
    listed.length > 0 ? t("travel.sitting", { names: sittingNames }) : "",
  ].filter((part) => part.length > 0);
  const acted = passengers.filter((passenger) =>
    leaveProfiles.some((profile) => profile.playerProfileId === passenger.playerProfileId));

  return (
    <Box
      sx={{
        flexShrink: 0,
        px: 1.4,
        py: 1.1,
        borderRadius: "11px",
        border: "1px solid",
        borderColor: colors.cardBorder,
        bgcolor: full ? colors.rideFull : colors.rideFree,
      }}
    >
      <Box sx={{ display: "flex", alignItems: "center", gap: 1, minWidth: 0 }}>
        <DirectionsCarOutlinedIcon sx={{ fontSize: 16, color: colors.muted, flexShrink: 0 }} />
        <Typography noWrap sx={{ fontWeight: 800, fontSize: 14, color: colors.ink, minWidth: 0 }}>
          {formatPersonName(ride.firstName, ride.lastName)}
        </Typography>
        {ride.seatsTotal > 0 && (
          <SeatDots
            total={ride.seatsTotal}
            taken={ride.seatsTaken}
            free={colors.dotFree}
            occupied={colors.dotTaken}
          />
        )}
        <Box sx={{ ml: "auto", flexShrink: 0, display: "flex", alignItems: "center", gap: 0.75 }}>
          {full && <TravelPill tone="warn">{t("travel.ride-full")}</TravelPill>}
          {departs && (
            <Typography component="span" sx={{ fontSize: 12, color: colors.muted, whiteSpace: "nowrap" }}>
              {t("travel.departs", { time: departs })}
            </Typography>
          )}
        </Box>
      </Box>
      {fixedList.length > 0 && (
        <Typography sx={{ mt: 0.5, fontSize: 12, fontWeight: 700, color: colors.ink }}>
          {t("travel.fixed-seat", {
            names: fixedList.map((person) => formatPersonName(person.firstName, person.lastName)).join(", "),
          })}
        </Typography>
      )}
      {(bits.length > 0 || ride.departurePlace) && (
        <Box sx={{ mt: 0.5, display: "flex", alignItems: "baseline", gap: 1, minWidth: 0, fontSize: 12 }}>
          {bits.length > 0 && (
            <Typography noWrap component="span" title={bits.join(" · ")} sx={{ fontSize: 12, color: colors.muted, minWidth: 0, flex: "1 1 auto" }}>
              {free > 0 && <Box component="b" sx={{ color: colors.okText }}>{bits[0]}</Box>}
              {free > 0 ? bits.slice(1).map((bit) => ` · ${bit}`).join("") : bits.join(" · ")}
            </Typography>
          )}
          {ride.departurePlace && (
            <Typography noWrap component="span" title={ride.departurePlace} sx={{ ml: "auto", fontSize: 12, color: colors.muted, minWidth: 0, flex: "0 1 auto", maxWidth: bits.length > 0 ? "45%" : "100%" }}>
              {ride.departurePlace}
            </Typography>
          )}
        </Box>
      )}
      {ride.notes && (
        <Typography sx={{ mt: 0.25, fontSize: 12, color: colors.muted, fontStyle: "italic" }}>
          {ride.notes}
        </Typography>
      )}
      {bookProfiles.length > 0 && (
        <Box sx={{ display: "flex", flexDirection: "column", gap: 0.75, mt: 1 }}>
          {bookProfiles.map((profile) => {
            const name = formatPersonName(profile.firstName, profile.lastName);
            return (
              <Button
                key={profile.playerProfileId}
                disabled={pending}
                onClick={() => onBook(profile.playerProfileId)}
                sx={{
                  width: "100%",
                  height: 30,
                  borderRadius: "8px",
                  textTransform: "none",
                  fontWeight: 700,
                  fontSize: 13,
                  color: colors.buttonText,
                  bgcolor: colors.button,
                  boxShadow: "none",
                  "&:hover": { bgcolor: colors.buttonHover, boxShadow: "none" },
                }}
              >
                {name ? t("travel.book-seat-for", { name }) : t("travel.book-seat")}
              </Button>
            );
          })}
        </Box>
      )}
      {acted.length > 0 && (
        <Box sx={{ display: "flex", flexDirection: "column", gap: 0.5, mt: 0.75 }}>
          {acted.map((passenger) => {
            return (
              <Box key={passenger.playerProfileId} sx={{ display: "flex", alignItems: "center", gap: 0.75, flexWrap: "wrap" }}>
                <Typography sx={{ fontSize: 12, color: colors.muted, mr: 0.5 }}>
                  {formatPersonName(passenger.firstName, passenger.lastName)}
                </Typography>
                <Button
                  size="small"
                  disabled={pending}
                  onClick={() => onLeave(passenger.playerProfileId)}
                  startIcon={<LogoutRoundedIcon />}
                  sx={quietButton(colors.ink, colors.cardBorder, colors.highlight)}
                >
                  {t("travel.leave-seat")}
                </Button>
              </Box>
            );
          })}
        </Box>
      )}
      {(canEdit || canDelete) && (
        <Box sx={{ display: "flex", gap: 0.75, mt: 0.75 }}>
          {canEdit && (
            <Button
              size="small"
              disabled={pending}
              onClick={onEdit}
              startIcon={<EditOutlinedIcon />}
              sx={quietButton(colors.ink, colors.cardBorder, colors.highlight)}
            >
              {t("common.edit")}
            </Button>
          )}
          {canDelete && (
            <Button
              size="small"
              disabled={pending}
              onClick={onDelete}
              startIcon={<DeleteOutlineRoundedIcon />}
              sx={quietButton(colors.errText, colors.errBorder, colors.errBg)}
            >
              {t("common.delete")}
            </Button>
          )}
        </Box>
      )}
    </Box>
  );
};

const quietButton = (color: string, border: string, hover: string) => ({
  minWidth: 0,
  height: 26,
  px: 1,
  borderRadius: "8px",
  border: "1px solid",
  borderColor: border,
  textTransform: "none" as const,
  fontWeight: 700,
  fontSize: 12,
  color,
  "& .MuiButton-startIcon": { mr: 0.5 },
  "& .MuiSvgIcon-root": { fontSize: 15 },
  "&:hover": { bgcolor: hover, borderColor: color },
});
