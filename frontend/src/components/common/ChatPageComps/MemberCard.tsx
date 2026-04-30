import { Avatar, Box, Chip, Typography } from '@mui/material';
import type { MemberInfo } from '../../../api/generated/model';
import { STAT_COLORS } from '../../../constants/colors';
import { useTranslation } from 'react-i18next';

interface MemberCardProps {
  member: MemberInfo;
  role: 'coach' | 'player';
}

export const MemberCard = ({ member, role }: MemberCardProps) => {
  const { t } = useTranslation();
  const fullName = `${member.firstName ?? ''} ${member.lastName ?? ''}`.trim();
  const initials = fullName
    .split(' ')
    .filter(w => w.length > 0)
    .map(w => w[0].toUpperCase())
    .join('')
    .substring(0, 2);

  const avatarColor = role === 'coach' ? '#10b981' : STAT_COLORS.blueAccent;

  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        px: 2,
        py: 1,
        borderRadius: 2,
        transition: 'background-color 0.2s ease',
        '&:hover': { bgcolor: 'action.hover' },
      }}
    >
      <Avatar sx={{ bgcolor: avatarColor, width: 36, height: 36, fontSize: '0.85rem', flexShrink: 0 }}>
        {initials}
      </Avatar>
      <Box sx={{ flexGrow: 1, overflow: 'hidden' }}>
        <Typography variant="body2" fontWeight={500} noWrap>
          {fullName || '—'}
        </Typography>
      </Box>
      <Chip
        label={t(`chat.${role}`)}
        size="small"
        sx={{
          fontSize: '0.7rem',
          height: 20,
          bgcolor: role === 'coach' ? 'rgba(16, 185, 129, 0.1)' : 'rgba(59, 130, 246, 0.1)',
          color: role === 'coach' ? '#10b981' : STAT_COLORS.blueAccent,
          border: 'none',
          flexShrink: 0,
        }}
      />
    </Box>
  );
};
