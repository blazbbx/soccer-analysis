import { Box, Typography } from "@mui/material";
import DirectionsCarOutlinedIcon from "@mui/icons-material/DirectionsCarOutlined";
import HelpOutlineIcon from "@mui/icons-material/HelpOutline";
import { useTranslation } from "react-i18next";
import type { AttendanceStatus, TravelRosterEntry } from "../../api/travel";
import { formatPersonName, getInitials } from "../../utils/stringUtils";
import { StatusChoices } from "./AttendanceRow";
import { needsSeat } from "./travelRules";
import { useTravelColors, type TravelColors } from "./travelStyle";
import { TravelAvatar, TravelPanelHeader, TravelPill, type TravelTone } from "./travelUi";

const byName = (left: TravelRosterEntry, right: TravelRosterEntry) =>
  formatPersonName(left.firstName, left.lastName).localeCompare(
    formatPersonName(right.firstName, right.lastName),
    undefined,
    { sensitivity: "base" },
  );

export const splitOpenPeople = (roster: TravelRosterEntry[]) => {
  const unseated = roster.filter((entry) => !entry.offerId);
  const going = roster.filter((entry) => entry.status === "GOING").sort((left, right) => {
    const leftOpen = left.offerId ? 1 : 0;
    const rightOpen = right.offerId ? 1 : 0;
    return leftOpen - rightOpen || byName(left, right);
  });
  return {
    going,
    requesting: unseated.filter(needsSeat).sort(byName),
    noResponse: roster
      .filter((entry) => !entry.status || entry.status === "NO_RESPONSE")
      .sort(byName),
    notGoing: roster.filter((entry) => entry.status === "NOT_GOING").sort(byName),
    maybe: roster.filter((entry) => entry.status === "MAYBE").sort(byName),
  };
};

type PersonTone = "going" | "not" | "maybe" | "none";

const pillTone = (tone: PersonTone, seat: boolean): TravelTone => {
  if (seat || tone === "going") return "ok";
  if (tone === "maybe") return "warn";
  if (tone === "not") return "err";
  return "mut";
};

const avatarColors = (colors: TravelColors, tone: PersonTone) => {
  if (tone === "going") return { bg: colors.chipGoingBg, fg: colors.chipGoingText };
  if (tone === "maybe") return { bg: colors.amberBg, fg: colors.amberText };
  if (tone === "not") return { bg: colors.errBg, fg: colors.errText };
  return { bg: colors.grayBg, fg: colors.grayText };
};

const PersonPanel = ({
  title,
  entries,
  tone,
  highlightedId,
  skipRequestingIds,
  canRespond,
  pending,
  onRespond,
}: {
  title: string;
  entries: TravelRosterEntry[];
  tone: PersonTone;
  highlightedId?: string;
  skipRequestingIds?: Set<string>;
  canRespond: (entry: TravelRosterEntry) => boolean;
  pending?: boolean;
  onRespond: (entry: TravelRosterEntry, status: AttendanceStatus) => void;
}) => {
  const { t } = useTranslation();
  const colors = useTravelColors();
  const avatar = avatarColors(colors, tone);
  const statusLabel = tone === "going"
    ? t("travel.status.going")
    : tone === "not"
      ? t("travel.child-status.not-going")
      : tone === "maybe"
        ? t("travel.status.maybe")
        : t("travel.status.no-response");

  return (
    <Box
      sx={{
        flexShrink: 0,
        px: 1.4,
        py: 1.1,
        borderRadius: "11px",
        border: "1px solid",
        borderColor: colors.cardBorder,
        bgcolor: colors.sub,
      }}
    >
      <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 1, mb: 0.5 }}>
        <Typography sx={{ fontWeight: 800, fontSize: 14, color: colors.ink }}>
          {title}
        </Typography>
        <TravelPill tone="count" strong>{entries.length}</TravelPill>
      </Box>
      {entries.map((entry) => {
        const name = formatPersonName(entry.firstName, entry.lastName);
        const showSeat = tone === "going" && needsSeat(entry) && !skipRequestingIds?.has(entry.playerProfileId);
        const answer = canRespond(entry);
        const highlighted = entry.playerProfileId === highlightedId;
        return (
          <Box key={entry.playerProfileId} sx={{ py: 0.6 }}>
            <Box sx={{ display: "flex", alignItems: "flex-start", gap: 1 }}>
              <TravelAvatar initials={getInitials(name)} size={26} bg={avatar.bg} fg={avatar.fg} />
              <Typography
                sx={{
                  flex: 1,
                  minWidth: 0,
                  fontWeight: highlighted ? 800 : 600,
                  fontSize: 13.5,
                  lineHeight: 1.3,
                  color: colors.ink,
                  whiteSpace: "normal",
                  overflowWrap: "anywhere",
                }}
              >
                {name}
              </Typography>
              <TravelPill
                tone={pillTone(tone, showSeat)}
                icon={showSeat ? <DirectionsCarOutlinedIcon /> : tone === "none" ? <HelpOutlineIcon /> : undefined}
              >
                {showSeat ? `${statusLabel} · ${t("travel.requesting-seat")}` : statusLabel}
              </TravelPill>
            </Box>
            {answer && (
              <Box sx={{ mt: 0.6, pl: 4.25 }}>
                <StatusChoices
                  status={entry.status ?? "NO_RESPONSE"}
                  pending={pending}
                  onRespond={(status) => onRespond(entry, status)}
                />
              </Box>
            )}
          </Box>
        );
      })}
    </Box>
  );
};

export const OpenPeopleList = ({
  roster,
  canRespond,
  pending,
  highlightedId,
  skipRequestingIds,
  onRespond,
}: {
  roster: TravelRosterEntry[];
  canRespond: (entry: TravelRosterEntry) => boolean;
  pending?: boolean;
  highlightedId?: string;
  skipRequestingIds?: string[];
  onRespond: (entry: TravelRosterEntry, status: AttendanceStatus) => void;
}) => {
  const { t } = useTranslation();
  const colors = useTravelColors();
  const groups = splitOpenPeople(roster);
  const skip = new Set(skipRequestingIds ?? []);
  const requesting = groups.requesting.filter((entry) => !skip.has(entry.playerProfileId));
  const requestingIds = new Set(requesting.map((entry) => entry.playerProfileId));
  const going = groups.going.filter((entry) => !requestingIds.has(entry.playerProfileId));
  const panels: { key: string; title: string; entries: TravelRosterEntry[]; tone: PersonTone }[] = [
    { key: "request", title: t("travel.requesting-seat"), entries: requesting, tone: "going" },
    { key: "going", title: t("travel.status.going"), entries: going, tone: "going" },
    { key: "not", title: t("travel.child-status.not-going"), entries: groups.notGoing, tone: "not" },
    { key: "maybe", title: t("travel.status.maybe"), entries: groups.maybe, tone: "maybe" },
    { key: "none", title: t("travel.status.no-response"), entries: groups.noResponse, tone: "none" },
  ];
  const visible = panels.filter((panel) => panel.entries.length > 0);

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
      <TravelPanelHeader title={t("travel.players")} />
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
        {visible.length === 0 && (
          <Typography sx={{ fontSize: 13, color: colors.muted }}>
            {t("travel.empty-open-people")}
          </Typography>
        )}
        {visible.map((panel) => (
          <PersonPanel
            key={panel.key}
            title={panel.title}
            entries={panel.entries}
            tone={panel.tone}
            highlightedId={highlightedId}
            skipRequestingIds={skip}
            canRespond={canRespond}
            pending={pending}
            onRespond={onRespond}
          />
        ))}
      </Box>
    </Box>
  );
};
