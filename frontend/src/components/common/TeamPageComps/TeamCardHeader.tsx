import { Box, Typography, Stack, IconButton } from '@mui/material';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import { STAT_COLORS } from '../../../constants/colors';
import { TeamDto } from '../../../types/team';

export const TeamCardHeader = ({ team }: { team: TeamDto }) => {
  return (
    <Stack 
      direction="row" 
      alignItems="center" 
      justifyContent="space-between" 
      sx={{ mb: 3 }}
    >
      {/* Bal oldal: Logó és Csapat infók */}
      <Stack direction="row" alignItems="center" spacing={2}>
        <Box 
          sx={{ 
            width: 56, 
            height: 56, 
            backgroundColor: STAT_COLORS.blueAccent, 
            borderRadius: 3, 
            display: 'flex', 
            alignItems: 'center', 
            justifyContent: 'center',
            color: 'white',
            fontWeight: 'bold',
            fontSize: '1.25rem'
          }}
        >
          {team.shortName}
        </Box>
        
        <Box>
          <Typography variant="h2" sx={{ fontSize: '1.25rem', color: 'text.primary' }}>
            {team.name}
          </Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.5 }}>
            {team.formation} &bull; {team.squad.length} players &bull; Coach: {team.coachName}
          </Typography>
        </Box>
      </Stack>

      {/* Jobb oldal: W D L Pts statisztikák és a nyíl */}
      <Stack direction="row" alignItems="center" spacing={3}>
        <Stack direction="row" spacing={2} sx={{ textAlign: 'center' }}>
          <Box>
            <Typography variant="body1" sx={{ color: STAT_COLORS.wins, fontWeight: 'bold' }}>{team.stats.wins}</Typography>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>W</Typography>
          </Box>
          <Box>
            <Typography variant="body1" sx={{ color: STAT_COLORS.draws, fontWeight: 'bold' }}>{team.stats.draws}</Typography>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>D</Typography>
          </Box>
          <Box>
            <Typography variant="body1" sx={{ color: STAT_COLORS.losses, fontWeight: 'bold' }}>{team.stats.losses}</Typography>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>L</Typography>
          </Box>
          <Box>
            <Typography variant="body1" sx={{ color: STAT_COLORS.points, fontWeight: 'bold' }}>{team.stats.points}</Typography>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>Pts</Typography>
          </Box>
        </Stack>
        
        <IconButton sx={{ color: 'text.secondary' }}>
          <KeyboardArrowUpIcon />
        </IconButton>
      </Stack>
    </Stack>
  );
};