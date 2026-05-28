import { Box, Typography, Avatar, Stack, IconButton, Tooltip } from '@mui/material';
import PersonRemoveIcon from '@mui/icons-material/PersonRemove';
import { STAT_COLORS } from '../../constants/colors';
import { getInitials } from '../../utils/stringUtils';
import { type MemberInfo } from '../../api/generated/model';
import { useTranslation } from 'react-i18next';

interface PlayerCardProps {
  player: MemberInfo;
  onRemove?: () => void;
}

export const PlayerCard = ({ player, onRemove }: PlayerCardProps) => {
  const { t } = useTranslation();
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
        <Avatar sx={{ bgcolor: '#10b981', width: 40, height: 40, fontWeight: 'bold' }}>
          {initials}
        </Avatar>
        <Box>
          <Typography variant="body1" sx={{ fontWeight: 500, color: 'text.primary' }}>
            {`${player.firstName} ${player.lastName}`}
          </Typography>
        </Box>
      </Stack>
      {onRemove && (
        <Tooltip title={t('teams.remove-player')}>
          <IconButton size="small" color="error" onClick={onRemove}>
            <PersonRemoveIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      )}
    </Stack>
  );
};