import { Avatar, Box, Typography } from '@mui/material';
import type { ChatMessageResponse } from '../../api/generated/model';

interface MessageBubbleProps {
  message: ChatMessageResponse;
  isCurrentUser: boolean;
}

const formatTime = (isoString: string): string => {
  const date = new Date(isoString);
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
};

export const MessageBubble = ({ message, isCurrentUser }: MessageBubbleProps) => {
  const fullName = message.senderName ?? '';
  const initials = fullName
    .split(' ')
    .filter(w => w.length > 0)
    .map(w => w[0].toUpperCase())
    .join('')
    .substring(0, 2);

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
          bgcolor: '#14b8a6',
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
          <Typography variant="caption" fontWeight={600} color="text.primary" sx={{ mb: 0.5 }}>
            {fullName}
          </Typography>
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
          {formatTime(message.createdAt ?? '')}
        </Typography>
      </Box>
    </Box>
  );
};
