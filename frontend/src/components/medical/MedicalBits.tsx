import { Box, Chip, Stack, Tooltip, Typography, type SxProps, type Theme } from "@mui/material";
import MonitorHeartOutlinedIcon from "@mui/icons-material/MonitorHeartOutlined";
import CheckRoundedIcon from "@mui/icons-material/CheckRounded";
import ScheduleOutlinedIcon from "@mui/icons-material/ScheduleOutlined";
import WarningAmberOutlinedIcon from "@mui/icons-material/WarningAmberOutlined";
import RemoveCircleOutlineIcon from "@mui/icons-material/RemoveCircleOutline";
import BlockIcon from "@mui/icons-material/Block";
import HourglassEmptyIcon from "@mui/icons-material/HourglassEmpty";
import HelpOutlineIcon from "@mui/icons-material/HelpOutline";
import VerifiedOutlinedIcon from "@mui/icons-material/VerifiedOutlined";
import { useTranslation } from "react-i18next";
import type { MedicalResult, MedicalState, TeamMedicalStatusItem } from "../../api/medical";
import { MEDICAL_COLORS, MEDICAL_UNSEEN_COLOR, medicalSentence, medicalShortLabel } from "./medicalFormat";

/** The line icon of a state. */
export const MedicalStateIcon = ({ state, sx }: { state: MedicalState; sx?: SxProps<Theme> }) => {
  switch (state) {
    case "VALID":
      return <CheckRoundedIcon sx={sx} />;
    case "EXPIRING":
      return <ScheduleOutlinedIcon sx={sx} />;
    case "UNVERIFIED":
      return <HourglassEmptyIcon sx={sx} />;
    case "EXPIRED":
      return <WarningAmberOutlinedIcon sx={sx} />;
    case "SUSPENDED":
    case "NOT_FIT":
      return <BlockIcon sx={sx} />;
    case "RESULT_MISSING":
      return <HelpOutlineIcon sx={sx} />;
    default:
      return <RemoveCircleOutlineIcon sx={sx} />;
  }
};

/** The light on a child card: only when the child needs a certificate and it is not VALID. */
export const MedicalDot = ({
  status,
  result,
  validUntil,
  daysRemaining,
  size = 10,
}: {
  status?: MedicalState;
  result?: MedicalResult;
  validUntil?: string;
  daysRemaining?: number;
  size?: number;
}) => {
  const { t, i18n } = useTranslation();
  if (!status || status === "VALID") return null;
  const sentence = medicalSentence(t, i18n.language, status, validUntil, daysRemaining, result);
  return (
    <Tooltip title={t("medical.tooltip", { sentence })} placement="right">
      <Box
        component="span"
        role="img"
        aria-label={t("medical.tooltip", { sentence })}
        sx={{
          display: "inline-block",
          width: size,
          height: size,
          borderRadius: "50%",
          bgcolor: MEDICAL_COLORS[status],
          boxShadow: (theme) => `0 0 0 2px ${theme.palette.background.paper}`,
          flexShrink: 0,
        }}
      />
    </Tooltip>
  );
};

/** The blue mark of new sport medical data someone else entered, apart from the light. */
export const MedicalUnseenDot = ({ size = 10 }: { size?: number }) => {
  const { t } = useTranslation();
  return (
    <Tooltip title={t("medical.unseen.tooltip")} placement="right">
      <Box
        component="span"
        role="img"
        aria-label={t("medical.unseen.tooltip")}
        sx={{
          display: "inline-block",
          width: size,
          height: size,
          borderRadius: "50%",
          bgcolor: MEDICAL_UNSEEN_COLOR,
          boxShadow: (theme) => `0 0 0 2px ${theme.palette.background.paper}`,
          flexShrink: 0,
        }}
      />
    </Tooltip>
  );
};

/** The short label on a roster row, with the full sentence in the tooltip and a tick when verified. */
export const MedicalRosterLabel = ({ item }: { item?: TeamMedicalStatusItem }) => {
  const { t, i18n } = useTranslation();
  if (!item) return null;
  const color = MEDICAL_COLORS[item.status];
  const sentence = medicalSentence(t, i18n.language, item.status, item.validUntil, item.daysRemaining, item.result);
  const label = medicalShortLabel(t, i18n.language, item.status, item.validUntil, item.daysRemaining);
  const training = item.result === "ALKALMAS" && (item.status === "VALID" || item.status === "EXPIRING");
  return (
    <Tooltip title={t("medical.tooltip", { sentence })}>
      <Chip
        size="small"
        variant="outlined"
        icon={item.verified
          ? <VerifiedOutlinedIcon sx={{ fontSize: 14, color: `${color} !important` }} />
          : <MedicalStateIcon state={item.status} sx={{ fontSize: 14, color: `${color} !important` }} />}
        label={training ? `${label} · ${t("medical.short.training")}` : label}
        sx={{
          height: 24,
          fontWeight: 700,
          fontVariantNumeric: "tabular-nums",
          color,
          bgcolor: `${color}14`,
          border: "1px solid",
          borderColor: `${color}55`,
          flexShrink: 0,
        }}
      />
    </Tooltip>
  );
};

/** VALID or EXPIRING with ALKALMAS, or a negative result: the paper does not allow competing. Unverified rows are not counted. */
export const notCompeting = (item: TeamMedicalStatusItem) =>
  ((item.status === "VALID" || item.status === "EXPIRING") && !item.resultAllowsCompeting)
  || item.status === "SUSPENDED"
  || item.status === "NOT_FIT";

/**
 * "1 ellenőrzésre vár · 2 lejáró · 1 nem versenyezhet" next to the squad title. Zero parts are hidden; all valid
 * shows nothing. The "waiting for verification" part can filter the roster to those players.
 */
export const MedicalSummaryLine = ({
  items,
  unverifiedOnly = false,
  onToggleUnverified,
}: {
  items?: TeamMedicalStatusItem[];
  unverifiedOnly?: boolean;
  onToggleUnverified?: () => void;
}) => {
  const { t } = useTranslation();
  if (!items || items.length === 0) return null;
  const count = (state: MedicalState) => items.filter((item) => item.status === state).length;
  const parts = [
    { key: "UNVERIFIED", color: MEDICAL_COLORS.UNVERIFIED, label: "medical.summary.unverified", n: count("UNVERIFIED"), toggle: true },
    { key: "EXPIRING", color: MEDICAL_COLORS.EXPIRING, label: "medical.summary.expiring", n: count("EXPIRING"), toggle: false },
    { key: "EXPIRED", color: MEDICAL_COLORS.EXPIRED, label: "medical.summary.expired", n: count("EXPIRED"), toggle: false },
    { key: "NOT_COMPETING", color: MEDICAL_COLORS.NOT_FIT, label: "medical.summary.not-competing", n: items.filter(notCompeting).length, toggle: false },
    { key: "RESULT_MISSING", color: MEDICAL_COLORS.RESULT_MISSING, label: "medical.summary.result-missing", n: count("RESULT_MISSING"), toggle: false },
    { key: "MISSING", color: MEDICAL_COLORS.MISSING, label: "medical.summary.missing", n: count("MISSING"), toggle: false },
  ].filter((part) => part.n > 0 || (part.toggle && unverifiedOnly));
  if (parts.length === 0) return null;
  return (
    <Stack direction="row" spacing={0.75} alignItems="center" flexWrap="wrap" useFlexGap sx={{ minWidth: 0 }}>
      <Tooltip title={t("medical.summary.tooltip")}>
        <MonitorHeartOutlinedIcon sx={{ fontSize: 16, color: "text.secondary" }} />
      </Tooltip>
      {parts.map((part, index) => {
        const content = (
          <Stack direction="row" spacing={0.75} alignItems="center">
            <Box component="span" aria-hidden sx={{ width: 7, height: 7, borderRadius: "50%", bgcolor: part.color }} />
            <Typography variant="caption" fontWeight={700} sx={{ color: part.color, whiteSpace: "nowrap" }}>
              {t(part.label, { count: part.n })}
            </Typography>
          </Stack>
        );
        return (
          <Stack key={part.key} direction="row" spacing={0.75} alignItems="center">
            {index > 0 && <Typography variant="caption" color="text.disabled">·</Typography>}
            {part.toggle && onToggleUnverified ? (
              <Tooltip title={t(unverifiedOnly ? "medical.summary.show-all" : "medical.summary.show-unverified")}>
                <Box
                  component="button"
                  type="button"
                  onClick={onToggleUnverified}
                  aria-pressed={unverifiedOnly}
                  sx={{
                    border: "1px solid",
                    borderColor: unverifiedOnly ? part.color : "transparent",
                    bgcolor: unverifiedOnly ? `${part.color}1a` : "transparent",
                    borderRadius: 2,
                    px: 0.75,
                    py: 0.25,
                    cursor: "pointer",
                    font: "inherit",
                  }}
                >
                  {content}
                </Box>
              </Tooltip>
            ) : content}
          </Stack>
        );
      })}
    </Stack>
  );
};
