import { Box, Typography, Stack } from "@mui/material";
import { STAT_COLORS } from "../../../constants/colors";
import { TeamStatsDto } from "../../../types/team";
import { useTranslation } from "react-i18next";

export const TeamStatsPanel = ({ stats }: { stats: TeamStatsDto }) => {
  const { t } = useTranslation();

  const statRows = [
    { label: t("teams.played"), value: stats.played, color: "text.primary" },
    { label: t("teams.wins"), value: stats.wins, color: STAT_COLORS.wins },
    { label: t("teams.draws"), value: stats.draws, color: STAT_COLORS.draws },
    { label: t("teams.losses"), value: stats.losses, color: STAT_COLORS.losses },
    { label: t("teams.points"), value: stats.points, color: STAT_COLORS.points },
  ];

  return (
    <Box
      sx={{
        backgroundColor: STAT_COLORS.panelBg,
        borderRadius: 3,
        p: 2.5,
        mb: 2,
      }}
    >
      <Typography
        variant="h3"
        sx={{ fontSize: "1rem", mb: 2, color: "text.primary" }}
      >
        {t("teams.stats")}
      </Typography>

      <Stack spacing={1.5}>
        {statRows.map((row) => (
          <Stack
            key={row.label}
            direction="row"
            justifyContent="space-between"
            alignItems="center"
          >
            <Typography variant="body2" sx={{ color: "text.secondary" }}>
              {row.label}
            </Typography>
            <Typography
              variant="body1"
              sx={{ color: row.color, fontWeight: 500 }}
            >
              {row.value}
            </Typography>
          </Stack>
        ))}
      </Stack>
    </Box>
  );
};
