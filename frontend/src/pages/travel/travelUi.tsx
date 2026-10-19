import type { ReactNode } from "react";
import { Box, ButtonBase, Tooltip, Typography } from "@mui/material";
import { useTravelColors, type TravelColors } from "./travelStyle";

export type TravelTone = "ok" | "warn" | "err" | "mut" | "info" | "count";

const toneColors = (colors: TravelColors, tone: TravelTone) => {
  if (tone === "ok") return { fg: colors.okText, bg: colors.okBg };
  if (tone === "warn") return { fg: colors.amberText, bg: colors.amberBg };
  if (tone === "err") return { fg: colors.errText, bg: colors.errBg };
  if (tone === "info") return { fg: colors.infoText, bg: colors.infoBg };
  if (tone === "count") return { fg: colors.pillText, bg: colors.pillBg };
  return { fg: colors.grayText, bg: colors.grayBg };
};

/** Small status pill, 12px text. */
export const TravelPill = ({
  tone,
  children,
  icon,
  strong,
  outlined,
  title,
}: {
  tone: TravelTone;
  children: ReactNode;
  icon?: ReactNode;
  strong?: boolean;
  outlined?: boolean;
  title?: string;
}) => {
  const colors = useTravelColors();
  const { fg, bg } = toneColors(colors, tone);
  return (
    <Box
      component="span"
      title={title}
      sx={{
        display: "inline-flex",
        alignItems: "center",
        gap: 0.5,
        px: tone === "count" ? 0.9 : 1,
        py: 0.2,
        minWidth: tone === "count" ? 24 : undefined,
        justifyContent: "center",
        borderRadius: "999px",
        flexShrink: 0,
        fontSize: 12,
        fontWeight: strong ? 800 : 700,
        lineHeight: 1.45,
        whiteSpace: "nowrap",
        color: fg,
        bgcolor: bg,
        border: "1px solid",
        borderColor: outlined ? fg : "transparent",
        "& .MuiSvgIcon-root": { fontSize: 13 },
      }}
    >
      {icon}
      {children}
    </Box>
  );
};

export const TravelAvatar = ({
  initials,
  size = 32,
  bg,
  fg,
}: {
  initials: string;
  size?: number;
  bg?: string;
  fg?: string;
}) => {
  const colors = useTravelColors();
  return (
    <Box
      sx={{
        width: size,
        height: size,
        borderRadius: "50%",
        flexShrink: 0,
        display: "grid",
        placeItems: "center",
        bgcolor: bg ?? colors.button,
        color: fg ?? colors.buttonText,
        fontSize: size >= 32 ? 12 : 11,
        fontWeight: 800,
        letterSpacing: 0.2,
      }}
    >
      {initials}
    </Box>
  );
};

export interface SegmentOption {
  key: string;
  label: string;
  icon?: ReactNode;
  tone?: "ok" | "err" | "warn" | "neutral";
  selected: boolean;
  disabled?: boolean;
  onClick: () => void;
}

/**
 * Segmented control. A disabled control (off) is faded to 35% with nothing selected and
 * explains itself in a tooltip.
 */
export const TravelSegmented = ({
  options,
  off,
  offHint,
  fullWidth,
  size = "md",
}: {
  options: SegmentOption[];
  off?: boolean;
  offHint?: string;
  fullWidth?: boolean;
  size?: "sm" | "md";
}) => {
  const colors = useTravelColors();
  const selectedStyle = (tone: SegmentOption["tone"]) => {
    if (tone === "ok") return { color: colors.okText, bgcolor: colors.okBg, borderColor: colors.okBorder };
    if (tone === "err") return { color: colors.errText, bgcolor: colors.errBg, borderColor: colors.errBorder };
    if (tone === "warn") return { color: colors.amberText, bgcolor: colors.amberBg, borderColor: colors.amberBorder };
    return { color: colors.ink, bgcolor: colors.segOn, borderColor: colors.cardBorder };
  };

  const control = (
    <Box
      role="group"
      aria-disabled={off || undefined}
      sx={{
        display: fullWidth ? "flex" : "inline-flex",
        width: fullWidth ? "100%" : undefined,
        boxSizing: "border-box",
        p: "2px",
        gap: "2px",
        borderRadius: "10px",
        border: "1px solid",
        borderColor: colors.cardBorder,
        bgcolor: colors.segBg,
        opacity: off ? 0.35 : 1,
        cursor: off ? "not-allowed" : undefined,
        flexShrink: 0,
      }}
    >
      {options.map((option) => {
        const on = !off && option.selected;
        return (
          <ButtonBase
            key={option.key}
            disabled={off || option.disabled}
            aria-pressed={on}
            onClick={option.onClick}
            sx={{
              flex: fullWidth ? 1 : undefined,
              gap: 0.6,
              px: size === "sm" ? 1 : 1.25,
              py: size === "sm" ? 0.45 : 0.7,
              borderRadius: "8px",
              border: "1px solid transparent",
              fontFamily: "inherit",
              fontSize: size === "sm" ? 12 : 12.5,
              fontWeight: 600,
              lineHeight: 1.3,
              whiteSpace: "nowrap",
              color: colors.muted,
              transition: "background-color .15s, color .15s",
              "& .MuiSvgIcon-root": { fontSize: 15, opacity: on ? 1 : 0.75 },
              "&:hover": off ? undefined : { color: colors.ink, bgcolor: colors.highlight },
              "&.Mui-disabled": { color: colors.muted },
              ...(on ? {
                ...selectedStyle(option.tone),
                "&:hover": selectedStyle(option.tone),
                "&.Mui-disabled": selectedStyle(option.tone),
              } : {}),
            }}
          >
            {option.icon}
            <span>{option.label}</span>
          </ButtonBase>
        );
      })}
    </Box>
  );

  if (off && offHint) {
    return (
      <Tooltip title={offHint} arrow>
        <Box component="span" sx={{ display: fullWidth ? "block" : "inline-block", cursor: "not-allowed" }}>
          {control}
        </Box>
      </Tooltip>
    );
  }
  return control;
};

/** Header row of a bottom panel (Szabad kocsik / Játékosok). */
export const TravelPanelHeader = ({ title, aside }: { title: string; aside?: ReactNode }) => {
  const colors = useTravelColors();
  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: 1,
        px: 1.5,
        py: 1,
        borderBottom: "1px solid",
        borderColor: colors.cardBorder,
        flexShrink: 0,
      }}
    >
      <Typography sx={{ fontSize: 12, fontWeight: 800, letterSpacing: 0.6, textTransform: "uppercase", color: colors.muted }}>
        {title}
      </Typography>
      {aside && (
        <Typography component="div" sx={{ fontSize: 12, color: colors.muted, whiteSpace: "nowrap" }}>
          {aside}
        </Typography>
      )}
    </Box>
  );
};
