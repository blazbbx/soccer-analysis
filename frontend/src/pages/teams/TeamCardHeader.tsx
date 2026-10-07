import { Box, Typography, Stack, IconButton } from '@mui/material';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import { STAT_COLORS } from '../../constants/colors';
import { useTranslation } from 'react-i18next';
import { type TeamCatalogItem } from '../../api/roleTeams';

export const TeamCardHeader = ({ team, isExpanded, onToggle}: { team: TeamCatalogItem, isExpanded:boolean, onToggle: () => void}) => {
  const { t } = useTranslation();  
  
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
          {team.clubName && (
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {team.clubName}
            </Typography>
          )}
          {typeof team.playerCount === 'number' && (
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {t('teams.roster-count', { players: team.playerCount, coaches: team.coachCount ?? 0 })}
            </Typography>
          )}
        </Box>
      </Stack>

      {/* Jobb oldal: W D L Pts statisztikák és a nyíl */}
      <Stack direction="row" alignItems="center" spacing={3}>        
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