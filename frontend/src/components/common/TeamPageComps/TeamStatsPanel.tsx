import { Box, Typography, Stack } from '@mui/material';
import { STAT_COLORS } from "../../../constants/colors";
import { TeamStatsDto } from "../../../types/team"

export const TeamStatsPanel = ({stats}:{stats:TeamStatsDto})=>{
    const statRows = [
    { label: 'Played', value: stats.played, color: 'text.primary' },
    { label: 'Wins', value: stats.wins, color: STAT_COLORS.wins },
    { label: 'Draws', value: stats.draws, color: STAT_COLORS.draws },
    { label: 'Losses', value: stats.losses, color: STAT_COLORS.losses },
    { label: 'Points', value: stats.points, color: STAT_COLORS.points },
  ];

  return (
    <Box 
      sx={{ 
        backgroundColor: STAT_COLORS.panelBg, 
        borderRadius: 3, 
        p: 2.5, 
        mb: 2 
      }}
    >
      <Typography variant="h3" sx={{ fontSize: '1rem', mb: 2, color: 'text.primary' }}>
        Season Stats
      </Typography>
      
      <Stack spacing={1.5}>
        {statRows.map((row) => (
          <Stack key={row.label} direction="row" justifyContent="space-between" alignItems="center">
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {row.label}
            </Typography>
            <Typography variant="body1" sx={{ color: row.color, fontWeight: 500 }}>
              {row.value}
            </Typography>
          </Stack>
        ))}
      </Stack>
    </Box>
  );
}