import { Box, Typography } from '@mui/material';
import { STAT_COLORS } from '../../../constants/colors';

export const FormationPanel = ({ formation }: { formation: string }) => {
  return (
    <Box 
      sx={{ 
        backgroundColor: STAT_COLORS.panelBg, 
        borderRadius: 3, 
        p: 2.5 
      }}
    >
      <Typography variant="h3" sx={{ fontSize: '1rem', mb: 1.5, color: 'text.primary' }}>
        Formation
      </Typography>
      
      <Typography 
        variant="h4" 
        sx={{ 
          fontSize: '1.75rem', 
          fontWeight: 'bold', 
          color: STAT_COLORS.blueAccent,
          mb: 0.5
        }}
      >
        {formation}
      </Typography>
      
      <Typography variant="body2" sx={{ color: 'text.secondary' }}>
        Active formation
      </Typography>
    </Box>
  );
};