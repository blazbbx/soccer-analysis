import { Box, Typography, Avatar, Stack } from '@mui/material';
import { STAT_COLORS } from '../../../constants/colors';
import { getInitials } from '../../../utils/stringUtils';
import { type MemberInfo } from '../../../api/generated/model';


export const PlayerCard = ( {player} : {player:MemberInfo}) => {
  const initials = getInitials(`${player.firstName} ${player.lastName}`);

  return (
    <Stack 
      direction="row" 
      alignItems="center" 
      justifyContent="space-between"
      sx={{
        backgroundColor: STAT_COLORS.panelBg,
        borderRadius: 3,
        p: 1.5,
        mb: 1, 
      }}
    >
      <Stack direction="row" alignItems="center" spacing={2}>
        {/* Játékos Avatar */}
        <Avatar sx={{ bgcolor: '#10b981', width: 40, height: 40, fontWeight: 'bold' }}>
          {initials}
        </Avatar>
        <Box>
          <Typography variant="body1" sx={{ fontWeight: 500, color: 'text.primary' }}>
            {`${player.firstName} ${player.lastName}`}
          </Typography>          
        </Box>
      </Stack>     
    </Stack>
  );
};