import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Box, CircularProgress, Divider, IconButton, TextField, Typography } from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import type { ChatMessageResponse } from '../../api/generated/model';
import { MessageBubble } from './MessageBubble';
import { useTranslation } from 'react-i18next';

interface ChatMainPanelProps {
  messages: ChatMessageResponse[];
  currentUserId: string;
  teamName: string | undefined;
  onSendMessage: (content: string) => void;
  hasMore: boolean;
  onLoadMore: () => void;
  isLoadingMore: boolean;
}

export const ChatMainPanel = ({ messages, currentUserId, teamName, onSendMessage, hasMore, onLoadMore, isLoadingMore }: ChatMainPanelProps) => {
  const { t } = useTranslation();
  const [input, setInput] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const topSentinelRef = useRef<HTMLDivElement>(null);
  const prevScrollHeightRef = useRef(0);
  const prevFirstIdRef = useRef<string | undefined>(undefined);

  // Save scroll height before prepend renders
  useEffect(() => {
    if (isLoadingMore) {
      prevScrollHeightRef.current = scrollContainerRef.current?.scrollHeight ?? 0;
    }
  }, [isLoadingMore]);

  // Restore scroll position after prepend, or scroll to bottom after append
  useLayoutEffect(() => {
    const container = scrollContainerRef.current;
    if (!container) return;

    const currentFirstId = messages[0]?.id;
    if (prevFirstIdRef.current && currentFirstId !== prevFirstIdRef.current) {
      container.scrollTop = container.scrollHeight - prevScrollHeightRef.current;
    } else {
      messagesEndRef.current?.scrollIntoView({ behavior: 'auto' });
    }

    prevFirstIdRef.current = currentFirstId;
    prevScrollHeightRef.current = container.scrollHeight;
  }, [messages]);

  // IntersectionObserver on top sentinel to trigger load-more
  useEffect(() => {
    const sentinel = topSentinelRef.current;
    if (!sentinel || !hasMore) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !isLoadingMore) onLoadMore();
      },
      { threshold: 0.1 },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasMore, isLoadingMore, onLoadMore]);

  const handleSend = () => {
    const trimmed = input.trim();
    if (!trimmed) return;
    onSendMessage(trimmed);
    setInput('');
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <Box sx={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', bgcolor: 'background.default' }}>
      <Box
        sx={{
          px: 3,
          py: 2,
          borderBottom: 1,
          borderColor: 'divider',
          bgcolor: 'background.paper',
          flexShrink: 0,
        }}
      >
        <Typography variant="h6" fontWeight={600}>
          {teamName ?? t('chat.title')}
        </Typography>
      </Box>

      <Box ref={scrollContainerRef} sx={{ flex: 1, overflowY: 'auto', px: 3, py: 2 }}>
        <div ref={topSentinelRef} />

        {isLoadingMore && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 1 }}>
            <CircularProgress size={20} sx={{ color: '#14b8a6' }} />
          </Box>
        )}

        {messages.length === 0 ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
            <Typography variant="body2" color="text.secondary">
              {t('chat.noMessages')}
            </Typography>
          </Box>
        ) : (
          messages.map(msg => (
            <MessageBubble
              key={msg.id}
              message={msg}
              isCurrentUser={msg.senderId === currentUserId}
            />
          ))
        )}
        <div ref={messagesEndRef} />
      </Box>

      <Divider />

      <Box
        sx={{
          px: 2,
          py: 1.5,
          display: 'flex',
          alignItems: 'flex-end',
          gap: 1,
          bgcolor: 'background.paper',
          flexShrink: 0,
        }}
      >
        <TextField
          fullWidth
          multiline
          maxRows={4}
          value={input}
          onChange={e => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={t('chat.inputPlaceholder')}
          variant="outlined"
          size="small"
          sx={{
            '& .MuiOutlinedInput-root': {
              borderRadius: 3,
              '& fieldset': { borderColor: 'divider' },
              '&:hover fieldset': { borderColor: 'text.secondary' },
              '&.Mui-focused fieldset': { borderColor: '#14b8a6' },
            },
          }}
        />
        <IconButton
          onClick={handleSend}
          disabled={!input.trim()}
          sx={{
            bgcolor: '#14b8a6',
            color: '#fff',
            borderRadius: 2,
            width: 40,
            height: 40,
            flexShrink: 0,
            '&:hover': { bgcolor: '#0f766e' },
            '&.Mui-disabled': { bgcolor: 'action.disabledBackground', color: 'action.disabled' },
            transition: 'background-color 0.2s ease',
          }}
        >
          <SendIcon fontSize="small" />
        </IconButton>
      </Box>
    </Box>
  );
};
