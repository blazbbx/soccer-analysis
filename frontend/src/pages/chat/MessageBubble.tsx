import { Avatar, Box, Tooltip, Typography, useTheme } from '@mui/material';
import { useTranslation } from 'react-i18next';
import type { ChatMessageResponse } from '../../api/generated/model';
import { formatPersonName, getInitials } from '../../utils/stringUtils';
import { SharedClipCard } from './SharedClipCard';
import { renderWithLinks } from './linkify';

interface MessageBubbleProps {
  message: ChatMessageResponse;
  isCurrentUser: boolean;
  clubName?: string;
}

const formatTime = (isoString: string): string => {
  const date = new Date(isoString);
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
};

export const MessageBubble = ({ message, isCurrentUser, clubName }: MessageBubbleProps) => {
  const { t, i18n } = useTranslation();
  const theme = useTheme();
  const fullName = message.senderFirstName || message.senderLastName
    ? formatPersonName(message.senderFirstName, message.senderLastName)
    : (message.senderName ?? '');
  const childNames = (message.senderChildNames ?? [])
    .map((child) => formatPersonName(child.firstName, child.lastName))
    .filter((name) => name.length > 0);
  const parentLabel = childNames.length > 0
    ? t('chat.parent-of', { names: childNames.join(', ') })
    : '';
  const isAdmin = message.senderRole === 'ADMIN';
  const isClubAdmin = message.senderRole === 'CLUB_ADMIN';
  const clubAdminLabel = isClubAdmin ? `${clubName?.trim() ? `${clubName.trim()} ` : ''}ADMIN` : '';
  const showName = Boolean(fullName) && (!isCurrentUser || Boolean(parentLabel) || isAdmin || isClubAdmin);
  const initials = getInitials(fullName);

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
        {clubAdminLabel && (
          <Typography variant="caption" fontWeight={700} sx={{ color: theme.palette.mode === 'dark' ? '#f5b942' : '#e65100', display: 'block', mb: 0.25 }}>
            {clubAdminLabel}
          </Typography>
        )}
        {(showName || parentLabel || isAdmin) && (
          <Typography variant="caption" fontWeight={600} color="text.primary" sx={{ mb: 0.5 }}>
            {showName && fullName}
            {isAdmin && (
              <Typography component="span" variant="caption" fontWeight={700} sx={{ color: '#d32f2f' }}>
                {showName && fullName ? ' · ADMIN' : 'ADMIN'}
              </Typography>
            )}
            {parentLabel && (
              <Typography component="span" variant="caption" color="text.secondary" fontWeight={400}>
                {(showName && fullName) || isAdmin ? ` · ${parentLabel}` : parentLabel}
              </Typography>
            )}
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
          {message.type === 'CLIP' && (
            message.clip ? (
              <Box
                sx={{
                  bgcolor: 'background.paper',
                  color: 'text.primary',
                  borderRadius: 2,
                  mx: -0.5,
                  mb: message.content ? 0.75 : 0,
                }}
              >
                <SharedClipCard clip={message.clip} />
              </Box>
            ) : (
              <Typography variant="body2" sx={{ fontStyle: 'italic', opacity: 0.8, mb: message.content ? 0.75 : 0 }}>
                {t('chat.clip-unavailable')}
              </Typography>
            )
          )}
          {message.content && (
            <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
              {renderWithLinks(message.content)}
            </Typography>
          )}
        </Box>

        <Tooltip
          title={message.createdAt
            ? new Date(message.createdAt).toLocaleString(i18n.language, { dateStyle: 'full', timeStyle: 'short' })
            : ''}
        >
          {/* Not selectable: copying a message must not glue the time onto its last word or link. */}
          <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, userSelect: 'none' }}>
            {formatTime(message.createdAt ?? '')}
          </Typography>
        </Tooltip>
      </Box>
    </Box>
  );
};
