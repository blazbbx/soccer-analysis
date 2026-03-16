import { Box, Typography, Stack, IconButton } from '@mui/material';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import { STAT_COLORS } from '../../../constants/colors';
import { TeamDto } from '../../../types/team';
import { useTranslation } from 'react-i18next';

export const TeamCardHeader = ({ team, isExpanded, onToggle}: { team: TeamDto, isExpanded:boolean, onToggle: () => void}) => {
  const {t} = useTranslation();
  
  
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
            {team.formation} &bull; {team.squad.length} {t("teams.players")} &bull; {t("teams.coach")} {team.coachName}
          </Typography>
        </Box>
      </Stack>

      {/* Jobb oldal: W D L Pts statisztikák és a nyíl */}
      <Stack direction="row" alignItems="center" spacing={3}>
        <Stack direction="row" spacing={2} sx={{ textAlign: 'center' }}>
          <Box>
            <Typography variant="body1" sx={{ color: STAT_COLORS.wins, fontWeight: 'bold' }}>{team.stats.wins}</Typography>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>{t("teams.statletters.W")}</Typography>
          </Box>
          <Box>
            <Typography variant="body1" sx={{ color: STAT_COLORS.draws, fontWeight: 'bold' }}>{team.stats.draws}</Typography>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>{t("teams.statletters.D")}</Typography>
          </Box>
          <Box>
            <Typography variant="body1" sx={{ color: STAT_COLORS.losses, fontWeight: 'bold' }}>{team.stats.losses}</Typography>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>{t("teams.statletters.L")}</Typography>
          </Box>
          <Box>
            <Typography variant="body1" sx={{ color: STAT_COLORS.points, fontWeight: 'bold' }}>{team.stats.points}</Typography>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>{t("teams.statletters.P")}</Typography>
          </Box>
        </Stack>
        
        <IconButton sx={{ color: 'text.secondary' }} onClick={onToggle}>
          <KeyboardArrowUpIcon 
            sx={{ 
              transform: isExpanded ? 'rotate(0deg)' : 'rotate(180deg)', 
              transition: 'transform 0.3s' 
            }} 
          />
        </IconButton>
      </Stack>
    </Stack>
  );
};