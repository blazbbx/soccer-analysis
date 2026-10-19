import { Box, Typography } from "@mui/material";
import CheckRoundedIcon from "@mui/icons-material/CheckRounded";
import CloseRoundedIcon from "@mui/icons-material/CloseRounded";
import HelpOutlineRoundedIcon from "@mui/icons-material/HelpOutlineRounded";
import DirectionsCarOutlinedIcon from "@mui/icons-material/DirectionsCarOutlined";
import ChatBubbleOutlineRoundedIcon from "@mui/icons-material/ChatBubbleOutlineRounded";
import dayjs from "dayjs";
import { useTranslation } from "react-i18next";
import { formatPersonName, getInitials } from "../../utils/stringUtils";
import type { AttendanceStatus, TravelRosterEntry } from "../../api/travel";
import { useTravelColors } from "./travelStyle";
import { TravelAvatar, TravelPill, TravelSegmented } from "./travelUi";

const STATUSES: AttendanceStatus[] = ["GOING", "NOT_GOING", "MAYBE"];

const statusKey = (status: AttendanceStatus) => {
  if (status === "NOT_GOING") return "travel.status.not-going";
  if (status === "MAYBE") return "travel.status.maybe";
  if (status === "NO_RESPONSE") return "travel.status.no-response";
  return "travel.status.going";
};

const statusIcon = (status: AttendanceStatus) => {
  if (status === "GOING") return <CheckRoundedIcon />;
  if (status === "NOT_GOING") return <CloseRoundedIcon />;
  return <HelpOutlineRoundedIcon />;
};

const statusTone = (status: AttendanceStatus) => {
  if (status === "GOING") return "ok" as const;
  if (status === "NOT_GOING") return "err" as const;
  return "warn" as const;
};

export const StatusChoices = ({
  status,
  pending,
  onRespond,
}: {
  status: AttendanceStatus;
  pending?: boolean;
  onRespond: (status: AttendanceStatus) => void;
}) => {
  const { t } = useTranslation();

  return (
    <TravelSegmented
      size="sm"
      options={STATUSES.map((next) => ({
        key: next,
        label: t(statusKey(next)),
        icon: statusIcon(next),
        tone: statusTone(next),
        selected: status === next,
        disabled: pending || status === next,
        onClick: () => onRespond(next),
      }))}
    />
  );
};

const childStatusKey = (status: AttendanceStatus) => {
  if (status === "NOT_GOING") return "travel.child-status.not-going";
  if (status === "MAYBE") return "travel.child-status.maybe";
  return "travel.child-status.going";
};

/** One row of the "children status" block: who, answer, ride wish. */
export const ChildStatusCard = ({
  entry,
  pending,
  highlighted,
  note,
  selfStatus,
  onRespond,
  onWantRide,
}: {
  entry: TravelRosterEntry;
  pending?: boolean;
  highlighted?: boolean;
  note?: "request" | "none" | "seated" | null;
  selfStatus?: boolean;
  onRespond: (status: AttendanceStatus) => void;
  onWantRide: (wantsRide: boolean) => void;
}) => {
  const { t } = useTranslation();
  const colors = useTravelColors();
  const status = entry.status ?? "NO_RESPONSE";
  const name = formatPersonName(entry.firstName, entry.lastName);
  const going = status === "GOING";
  const answeredAt = entry.respondedAt ? dayjs(entry.respondedAt) : null;
  const answered = answeredAt?.isValid() ? answeredAt.format("MM.DD. H:mm") : "";
  const pill = note === "request"
    ? <TravelPill tone="warn">{t("travel.car-no-seat")}</TravelPill>
    : note === "seated"
      ? <TravelPill tone="ok">{t("travel.car-has-seat")}</TravelPill>
      : note === "none"
        ? <TravelPill tone="mut">{t("travel.status.no-response")}</TravelPill>
        : null;

  return (
    <Box
      sx={{
        position: "relative",
        display: "grid",
        gridTemplateColumns: "minmax(0, 1fr)",
        "@container travel-kids (min-width: 560px)": {
          gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
          "& > :first-of-type": { gridColumn: "1 / -1" },
        },
        "@container travel-kids (min-width: 700px)": {
          gridTemplateColumns: "minmax(0, 1fr) auto auto",
          "& > :first-of-type": { gridColumn: "auto" },
        },
        alignItems: "center",
        columnGap: 1.5,
        rowGap: 1,
        px: 1.75,
        py: 1.1,
        borderTop: "1px solid",
        borderColor: colors.cardBorder,
        bgcolor: status === "NO_RESPONSE" ? colors.todoBg : "transparent",
        "&:first-of-type": { borderTop: 0 },
        "&::before": highlighted ? {
          content: '""',
          position: "absolute",
          left: 0,
          top: 8,
          bottom: 8,
          width: 3,
          borderRadius: "0 3px 3px 0",
          bgcolor: colors.button,
        } : undefined,
      }}
    >
      <Box sx={{ display: "flex", alignItems: "center", gap: 1.25, minWidth: 0 }}>
        <TravelAvatar initials={getInitials(name)} />
        <Box sx={{ minWidth: 0 }}>
          <Typography noWrap sx={{ fontWeight: 800, fontSize: 14, lineHeight: 1.3, color: colors.ink }}>
            {name}
          </Typography>
          {(pill || answered) && (
            <Box sx={{ mt: 0.25, display: "flex", alignItems: "center", flexWrap: "wrap", columnGap: 0.75, rowGap: 0.25, minWidth: 0 }}>
              {pill}
              {answered && (
                <Typography noWrap sx={{ fontSize: 12, color: colors.muted, minWidth: 0 }}>
                  {t("travel.answered-at", { when: answered })}
                </Typography>
              )}
            </Box>
          )}
          {entry.comment && (
            <Box sx={{ mt: 0.25, display: "flex", alignItems: "center", gap: 0.5, color: colors.muted, minWidth: 0 }}>
              <ChatBubbleOutlineRoundedIcon sx={{ fontSize: 13, flexShrink: 0 }} />
              <Typography noWrap sx={{ fontSize: 12, fontStyle: "italic", color: "inherit", minWidth: 0 }} title={entry.comment}>
                {entry.comment}
              </Typography>
            </Box>
          )}
        </Box>
      </Box>
      <TravelSegmented
        fullWidth
        options={STATUSES.map((next) => ({
          key: next,
          label: t(childStatusKey(next)),
          icon: statusIcon(next),
          tone: statusTone(next),
          selected: status === next,
          disabled: pending || status === next,
          onClick: () => onRespond(next),
        }))}
      />
      <TravelSegmented
        fullWidth
        off={!going}
        offHint={t(selfStatus ? "travel.ride-only-going-self" : "travel.ride-only-going")}
        options={[true, false].map((next) => {
          const current = going && (entry.wantsRide === true) === next;
          return {
            key: String(next),
            label: t(next ? "travel.want-ride" : "travel.no-ride"),
            icon: next ? <DirectionsCarOutlinedIcon /> : undefined,
            tone: next ? "ok" as const : "neutral" as const,
            selected: current,
            disabled: pending || current,
            onClick: () => onWantRide(next),
          };
        })}
      />
    </Box>
  );
};

export const AttendanceRow = ({
  entry,
  canRespond,
  pending,
  chip,
  highlighted,
  showChip = true,
  caption,
  captionColor,
  onRespond,
}: {
  entry: TravelRosterEntry;
  canRespond: boolean;
  pending?: boolean;
  chip: "request" | "quiet";
  highlighted?: boolean;
  showChip?: boolean;
  caption?: string;
  captionColor?: string;
  onRespond: (status: AttendanceStatus) => void;
}) => {
  const { t } = useTranslation();
  const colors = useTravelColors();
  const status = entry.status ?? "NO_RESPONSE";
  const quietLabel = t(statusKey(status));

  return (
    <Box
      sx={{
        py: 0.75,
        px: highlighted ? 1 : 0,
        display: "flex",
        alignItems: "center",
        flexWrap: "wrap",
        gap: 1,
        borderRadius: "10px",
        bgcolor: highlighted ? colors.highlight : "transparent",
        outline: highlighted ? `1px solid ${colors.button}` : "none",
      }}
    >
      <Box sx={{ flex: "1 1 140px", minWidth: 0 }}>
        <Typography sx={{ fontWeight: highlighted ? 800 : 700, fontSize: 14, lineHeight: 1.35, color: colors.ink }}>
          {formatPersonName(entry.firstName, entry.lastName)}
        </Typography>
        {caption && (
          <Typography sx={{ fontSize: 13, fontWeight: 700, lineHeight: 1.3, color: captionColor ?? colors.muted }}>
            {caption}
          </Typography>
        )}
        {entry.comment && (
          <Typography sx={{ fontSize: 12, color: colors.muted }}>
            {entry.comment}
          </Typography>
        )}
      </Box>
      {canRespond && (
        <StatusChoices status={status} pending={pending} onRespond={onRespond} />
      )}
      {showChip && <Box
        sx={{
          px: 1,
          py: 0.25,
          borderRadius: "999px",
          flexShrink: 0,
          fontSize: 12,
          fontWeight: 700,
          border: "1px solid",
          borderColor: chip === "request" ? colors.amberBorder : "transparent",
          bgcolor: chip === "request" ? colors.amberBg : colors.grayBg,
          color: chip === "request" ? colors.amberText : colors.grayText,
        }}
      >
        {chip === "request" ? t("travel.requesting-seat") : quietLabel}
      </Box>}
    </Box>
  );
};
