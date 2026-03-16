import { Box, Typography } from '@mui/material';
import { STAT_COLORS } from '../../../constants/colors';
import { useTranslation } from 'react-i18next';

export const FormationPanel = ({ formation }: { formation: string }) => {
  const {t} = useTranslation();

  return (
    <Box 
      sx={{ 
        backgroundColor: STAT_COLORS.panelBg, 
        borderRadius: 3, 
        p: 2.5 
      }}
    >
      <Typography variant="h3" sx={{ fontSize: '1rem', mb: 1.5, color: 'text.primary' }}>
        {t("teams.formation")}
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
        {t("teams.active-formation")}
      </Typography>
    </Box>
  );
};