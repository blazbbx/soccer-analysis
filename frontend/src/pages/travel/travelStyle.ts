import { alpha, darken, lighten, useTheme } from "@mui/material/styles";
import { APP_COLORS } from "../../constants/colors";

export interface TravelColors {
  page: string;
  card: string;
  cardBorder: string;
  selected: string;
  ink: string;
  muted: string;
  stat: string;
  pillBg: string;
  pillText: string;
  button: string;
  buttonHover: string;
  buttonText: string;
  amberBg: string;
  amberText: string;
  amberBorder: string;
  grayBg: string;
  grayText: string;
  dotFree: string;
  dotTaken: string;
  chipGoingBg: string;
  chipGoingText: string;
  rideFree: string;
  rideFull: string;
  highlight: string;
  /** Border of brand-coloured outlined controls. */
  brandBorder: string;
  /** Nested panel inside a card (children block, people groups, counters). */
  sub: string;
  /** Background of segmented controls. */
  segBg: string;
  /** Selected segment of a neutral segmented control. */
  segOn: string;
  okText: string;
  okBg: string;
  okBorder: string;
  errText: string;
  errBg: string;
  errBorder: string;
  infoText: string;
  infoBg: string;
  todoBg: string;
}

const BLUE = { dark: "#60a5fa", light: "#1d4ed8" };

/**
 * Lejutás colours. Neutrals and statuses come from the MUI theme (background, text, divider,
 * success, warning, error), so the page follows the app's dark and light theme. The brand accent
 * is the app's existing action colour (APP_COLORS.filledAction), because the theme's primary is
 * black / white.
 */
export const useTravelColors = (): TravelColors => {
  const { palette } = useTheme();
  const mode = palette.mode === "dark" ? "dark" : "light";
  const dark = mode === "dark";
  const base = palette.background.default;
  const ink = palette.text.primary;
  const muted = palette.text.secondary;
  const line = palette.divider;
  const brand = APP_COLORS.filledAction.main;
  const brandHover = APP_COLORS.filledAction.hover;
  const brandText = dark ? lighten(brand, 0.25) : brandHover;
  const green = palette.success.main;
  const greenText = dark ? palette.success.light : palette.success.dark;
  const amber = dark ? palette.warning.main : palette.warning.dark;
  // The dark theme's error.main is a deep red meant as a fill; its contrastText is the readable red.
  const red = dark ? palette.error.contrastText : palette.error.main;
  const card = dark ? lighten(base, 0.035) : palette.background.paper;
  const sub = dark ? alpha(ink, 0.035) : alpha(brand, 0.045);

  return {
    page: dark ? base : darken(base, 0.035),
    card,
    cardBorder: line,
    selected: brand,
    ink,
    muted,
    stat: sub,
    pillBg: alpha(brand, dark ? 0.14 : 0.1),
    pillText: brandText,
    button: brand,
    buttonHover: brandHover,
    buttonText: APP_COLORS.filledAction.contrastText,
    amberBg: alpha(amber, dark ? 0.14 : 0.1),
    amberText: amber,
    amberBorder: alpha(amber, 0.4),
    grayBg: alpha(muted, dark ? 0.16 : 0.12),
    grayText: muted,
    dotFree: green,
    dotTaken: red,
    chipGoingBg: alpha(green, dark ? 0.14 : 0.1),
    chipGoingText: greenText,
    rideFree: alpha(brand, dark ? 0.07 : 0.05),
    rideFull: alpha(muted, 0.07),
    highlight: alpha(brand, dark ? 0.1 : 0.08),
    brandBorder: alpha(brand, 0.5),
    sub,
    segBg: dark ? base : palette.background.paper,
    segOn: card,
    okText: greenText,
    okBg: alpha(green, dark ? 0.14 : 0.1),
    okBorder: alpha(green, 0.45),
    errText: red,
    errBg: alpha(red, dark ? 0.14 : 0.08),
    errBorder: alpha(red, 0.45),
    infoText: BLUE[mode],
    infoBg: alpha(BLUE[mode], dark ? 0.14 : 0.1),
    todoBg: alpha(amber, 0.05),
  };
};
