import { Avatar, Box, Chip, Typography } from '@mui/material';
import type { ChatMessage } from '../../../types/chat';
import { STAT_COLORS } from '../../../constants/colors';
import { useTranslation } from 'react-i18next';

interface MessageBubbleProps {
  message: ChatMessage;
  isCurrentUser: boolean;
}

const formatTime = (isoString: string): string => {
  const date = new Date(isoString);
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
};

export const MessageBubble = ({ message, isCurrentUser }: MessageBubbleProps) => {
  const { t } = useTranslation();
  const fullName = `${message.senderFirstName} ${message.senderLastName}`.trim();
  const initials = fullName
    .split(' ')
    .filter(w => w.length > 0)
    .map(w => w[0].toUpperCase())
    .join('')
    .substring(0, 2);

  const avatarColor =
    message.senderRole === 'coach'
      ? '#10b981'
      : message.senderRole === 'admin'
      ? '#0f766e'
      : STAT_COLORS.blueAccent;

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: isCurrentUser ? 'row-reverse' : 'row',
        alignItems: 'flex-end',
        gap: isCurrentUser ? 1 : 2,
        mb: 2,
        p: 2
      }}
    >
      <Avatar
        sx={{
          bgcolor: avatarColor,
          width: 32,
          height: 32,
          fontSize: '0.75rem',
          flexShrink: 0,
        }}
      >
        {initials}
      </Avatar>

      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: isCurrentUser ? 'flex-end' : 'flex-start',
          maxWidth: '65%',
        }}
      >
        {!isCurrentUser && (
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mb: 0.5 }}>
            <Typography variant="caption" fontWeight={600} color="text.primary">
              {fullName}
            </Typography>
            <Chip
              label={t(`chat.${message.senderRole}`)}
              size="small"
              sx={{
                fontSize: '0.65rem',
                height: 16,
                bgcolor:
                  message.senderRole === 'coach'
                    ? 'rgba(16, 185, 129, 0.1)'
                    : 'rgba(59, 130, 246, 0.1)',
                color:
                  message.senderRole === 'coach' ? '#10b981' : STAT_COLORS.blueAccent,
                border: 'none',
              }}
            />
          </Box>
        )}

        <Box
          sx={{
            px: 1.5,
            py: 1,
            borderRadius: isCurrentUser ? '12px 12px 4px 12px' : '12px 12px 12px 4px',
            bgcolor: isCurrentUser ? '#14b8a6' : 'background.default',
            border: isCurrentUser ? 'none' : '1px solid',
            borderColor: 'divider',
            color: isCurrentUser ? '#fff' : 'text.primary',
          }}
        >
          <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
            {message.content}
          </Typography>
        </Box>

        <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5 }}>
          {formatTime(message.sentAt)}
        </Typography>
      </Box>
    </Box>
  );
};
