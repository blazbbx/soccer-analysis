import { Box, Typography, Avatar, Chip, Stack, Card, useTheme } from "@mui/material";
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import EditNoteIcon from '@mui/icons-material/EditNote';
import { type MatchResponse } from "../../../api/generated/model/matchResponse";
import {APP_COLORS , STAT_COLORS} from  "../../../constants/colors" 
import {getInitials} from "../../../utils/stringUtils"

interface MatchCardProps {
  match: MatchResponse;
  onOpen: (matchId: string | undefined) => void;
}

const getStatusInfo = (status: MatchResponse['encodingStatus']) => {
  switch (status) {
    case 'UPLOADED': 
      return { color: STAT_COLORS.points, text: 'FELTÖLTVE' }; 
    case 'ENCODING': 
      return { color: STAT_COLORS.blueAccent, text: 'KÓDOLÁS' }; 
    case 'COMPLETED': 
      return { color: APP_COLORS.sideBarButton.active, text: 'KÉSZ' }; 
    case 'FAILED': 
      return { color: STAT_COLORS.losses, text: 'HIBA' }; 
    default: 
      return { color: STAT_COLORS.draws, text: 'ISMERETLEN' }; 
  }
};

export const MatchCard = ({ match, onOpen }: MatchCardProps) => {
  const theme = useTheme();
  const statusInfo = getStatusInfo(match.encodingStatus);
  const isDisabled = match.encodingStatus === 'UPLOADED';

  const homeTeamName = match.homeTeamName;
  const awayTeamName = match.awayTeamName;

  return (
    <Card 
      elevation={0} 
      sx={{ 
        bgcolor: 'background.paper',
        border: `1px solid ${theme.palette.divider}`, 
        borderRadius: theme.shape.borderRadius, 
        p: 2,
        width: '100%',
        transition: 'border-color 0.2s ease',
        '&:hover': {
          borderColor: isDisabled ? theme.palette.divider : APP_COLORS.sideBarButton.active,
        }
      }}
    >
      <Stack 
        direction="row" 
        spacing={2} 
        alignItems="center" 
        justifyContent="space-between" 
        width="100%"
      >
        {/* Balra: Státusz */}
        <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: '120px' }}>
          <Box component="span" sx={{ color: statusInfo.color, fontSize: '1.2rem', lineHeight: 1 }}>
            ●
          </Box>
          <Typography variant="body2" sx={{ color: statusInfo.color, fontWeight: 500 }}>
            {statusInfo.text}
          </Typography>
        </Stack>

        {/* Középre: Csapatok */}
        <Stack direction="row" spacing={2} alignItems="center" flexGrow={1} justifyContent="center">
          <Stack direction="row" spacing={1.5} alignItems="center">
            <Typography variant="h6" color="text.primary">
              {homeTeamName}
            </Typography>
            <Avatar 
              sx={{ 
                bgcolor: STAT_COLORS.blueAccent, 
                color: '#fff',
                width: 32, 
                height: 32, 
                fontSize: '1rem', 
                fontWeight: 500 
              }}
            >
              {getInitials(homeTeamName)}
            </Avatar>
          </Stack>
          
          <Typography variant="body2" color="text.secondary" fontWeight={500}>
            vs
          </Typography>
          
          <Stack direction="row" spacing={1.5} alignItems="center">
            <Avatar 
              sx={{ 
                bgcolor: STAT_COLORS.losses, 
                color: '#fff',
                width: 32, 
                height: 32, 
                fontSize: '1rem', 
                fontWeight: 500 
              }}
            >
              {getInitials(awayTeamName)}
            </Avatar>
            <Typography variant="h6" color="text.primary">
              {awayTeamName}
            </Typography>
          </Stack>
        </Stack>

        {/* Jobbra: Dátum és Gomb */}
        <Stack direction="row" spacing={3} alignItems="center">
          <Stack spacing={0.5} alignItems="flex-end">
             <Stack direction="row" spacing={1} alignItems="center">
                <CalendarTodayIcon sx={{ fontSize: '1rem', color: 'text.secondary' }} />
                <Typography variant="body2" color="text.secondary">
                   {match.matchDate?.split('T')[0]}
                </Typography>
             </Stack>
             
             <Chip
                label="Editor"
                icon={<EditNoteIcon sx={{ color: 'inherit !important' }}/>}
                size="small"
                onClick={isDisabled ? undefined : () => onOpen(match.id)}
                sx={{
                  bgcolor: isDisabled ? theme.palette.secondary.main : APP_COLORS.sideBarButton.activeBackGround,
                  color: isDisabled ? theme.palette.text.secondary : APP_COLORS.sideBarButton.active,
                  fontWeight: 500,
                  cursor: isDisabled ? 'default' : 'pointer',
                  border: 'none',
                  transition: 'background-color 0.2s ease',
                  '&:hover': {
                    bgcolor: isDisabled 
                      ? theme.palette.secondary.main 
                      : APP_COLORS.sideBarButton.activeHoverBackGround,
                  },
                  '& .MuiChip-icon': {
                    color: isDisabled ? theme.palette.text.secondary : APP_COLORS.sideBarButton.active,
                  },
                }}
             />
          </Stack>
          
        </Stack>
      </Stack>
    </Card>
  );
};