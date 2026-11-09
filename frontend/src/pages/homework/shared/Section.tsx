import type { ReactNode } from "react";
import { Box, Stack, Typography, type SxProps, type Theme } from "@mui/material";
import { alpha } from "@mui/material/styles";

/** A small uppercase heading, the same on every card of the homework pages. */
export const SectionLabel = ({ icon, children }: { icon?: ReactNode; children: ReactNode }) => (
  <Stack direction="row" spacing={0.75} sx={{ alignItems: "center", color: "text.secondary", "& svg": { fontSize: 16 } }}>
    {icon}
    <Typography variant="caption" sx={{ fontWeight: 700, letterSpacing: "0.08em", textTransform: "uppercase" }}>
      {children}
    </Typography>
  </Stack>
);

/**
 * One part of the page on its own see-through card, so the task, the status and the comments read as separate.
 * `fill` grows it to the height it is given and lets its content do the same, for a column whose bottom lines up.
 */
export const Section = ({
  title,
  icon,
  action,
  fill = false,
  sx,
  children,
}: {
  title?: ReactNode;
  icon?: ReactNode;
  /** On the right of the heading, such as a count or a filter. */
  action?: ReactNode;
  fill?: boolean;
  sx?: SxProps<Theme>;
  children: ReactNode;
}) => (
  <Box
    sx={[(theme) => ({
      p: { xs: 2, md: 2.5 },
      borderRadius: 3,
      border: 1,
      borderColor: "divider",
      bgcolor: alpha(theme.palette.background.paper, theme.palette.mode === "dark" ? 0.55 : 0.85),
      backdropFilter: "blur(8px)",
      minWidth: 0,
      ...(fill ? { display: "flex", flexDirection: "column", flexGrow: 1, minHeight: 0 } : {}),
    }), ...(Array.isArray(sx) ? sx : [sx])]}
  >
    <Stack spacing={1.5} sx={fill ? { flexGrow: 1, minHeight: 0 } : undefined}>
      {(title || action) && (
        <Stack direction="row" spacing={1} sx={{ alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", rowGap: 1 }}>
          {title ? <SectionLabel icon={icon}>{title}</SectionLabel> : <span />}
          {action}
        </Stack>
      )}
      {children}
    </Stack>
  </Box>
);

/** A bordered box inside a card, for one question. */
export const InnerBox = ({ children }: { children: ReactNode }) => (
  <Box sx={{ p: { xs: 1.5, md: 2 }, borderRadius: 2, border: 1, borderColor: "divider", minWidth: 0 }}>
    {children}
  </Box>
);
