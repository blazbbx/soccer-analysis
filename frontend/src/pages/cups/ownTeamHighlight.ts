import { alpha, type Theme } from "@mui/material/styles";

/**
 * 2.8: the caller's own team stands out: a 3 px stripe on the left and a faint background,
 * both from the theme's success colour so it works in light and dark mode.
 */
export const ownRowSx = {
  bgcolor: (theme: Theme) => alpha(theme.palette.success.main, 0.08),
  boxShadow: (theme: Theme) => `inset 3px 0 0 ${theme.palette.success.main}`,
};

/** Table rows: the stripe goes on the first cell, a table row cannot draw its own inset shadow. */
export const ownTableRowSx = {
  bgcolor: (theme: Theme) => alpha(theme.palette.success.main, 0.08),
  "& > td:first-of-type": {
    boxShadow: (theme: Theme) => `inset 3px 0 0 ${theme.palette.success.main}`,
  },
};
