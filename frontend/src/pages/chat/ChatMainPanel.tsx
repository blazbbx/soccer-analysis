import { Fragment, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Box, Chip, CircularProgress, Divider, IconButton, TextField, Typography } from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import { isAxiosError } from 'axios';
import type { ChatMessageResponse } from '../../api/generated/model';
import { useSnackbar } from '../../context/SnackbarContext';
import { MessageBubble } from './MessageBubble';
import { dayKey, dayLabel } from './chatDates';
import { useTranslation } from 'react-i18next';

// Closer to the bottom than this counts as "at the bottom".
const AT_BOTTOM_PX = 80;

interface ChatMainPanelProps {
  teamId: string;
  messages: ChatMessageResponse[];
  currentUserId: string;
  teamName: string | undefined;
  teamClubName?: string;
  onSendMessage: (content: string) => Promise<void>;
  hasMore: boolean;
  onLoadMore: () => void;
  isLoadingMore: boolean;
}

export const ChatMainPanel = ({ teamId, messages, currentUserId, teamName, teamClubName, onSendMessage, hasMore, onLoadMore, isLoadingMore }: ChatMainPanelProps) => {
  const { t, i18n } = useTranslation();
  const { showError } = useSnackbar();
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const scrollContainerRef = useRef<HTMLDivElement>(null);
  const topSentinelRef = useRef<HTMLDivElement>(null);
  const prevScrollHeightRef = useRef(0);
  const prevFirstIdRef = useRef<string | undefined>(undefined);
  const prevLastIdRef = useRef<string | undefined>(undefined);
  const prevCountRef = useRef(0);
  const prevTeamIdRef = useRef<string | undefined>(undefined);
  const isAtBottomRef = useRef(true);
  // Messages that arrived while the user was reading further up.
  const [newCount, setNewCount] = useState(0);

  const scrollToBottom = () => {
    const container = scrollContainerRef.current;
    if (!container) return;
    container.scrollTop = container.scrollHeight;
    isAtBottomRef.current = true;
    setNewCount(0);
  };

  const handleScroll = () => {
    const container = scrollContainerRef.current;
    if (!container) return;
    isAtBottomRef.current = container.scrollHeight - container.scrollTop - container.clientHeight < AT_BOTTOM_PX;
    if (isAtBottomRef.current && newCount > 0) setNewCount(0);
  };

  // Save scroll height before prepend renders
  useEffect(() => {
    if (isLoadingMore) {
      prevScrollHeightRef.current = scrollContainerRef.current?.scrollHeight ?? 0;
    }
  }, [isLoadingMore]);

  // Team switch or first load: to the bottom. Older page: keep the position. New message: to the bottom
  // if the user was there or sent it, otherwise count it for the "new messages" chip.
  useLayoutEffect(() => {
    const container = scrollContainerRef.current;
    if (!container) return;

    const firstId = messages[0]?.id;
    const last = messages[messages.length - 1];
    const lastId = last?.id;
    if (teamId !== prevTeamIdRef.current || !prevLastIdRef.current) {
      scrollToBottom();
    } else if (firstId !== prevFirstIdRef.current && lastId === prevLastIdRef.current) {
      container.scrollTop = container.scrollHeight - prevScrollHeightRef.current;
    } else if (lastId !== prevLastIdRef.current) {
      if (isAtBottomRef.current || last?.senderId === currentUserId) {
        scrollToBottom();
      } else {
        const added = Math.max(1, messages.length - prevCountRef.current);
        setNewCount(count => count + added);
      }
    }

    prevTeamIdRef.current = teamId;
    prevFirstIdRef.current = firstId;
    prevLastIdRef.current = lastId;
    prevCountRef.current = messages.length;
    prevScrollHeightRef.current = container.scrollHeight;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [messages, teamId]);

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

  // The field is cleared only after the server saved the message; on error the text stays.
  const handleSend = async () => {
    const trimmed = input.trim();
    if (!trimmed || sending) return;
    setSending(true);
    try {
      await onSendMessage(trimmed);
      setInput('');
    } catch (err) {
      // A 403 is already shown by the axios interceptor.
      if (!(isAxiosError(err) && err.response?.status === 403)) showError(t('chat.send-failed'));
    } finally {
      setSending(false);
    }
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

      <Box sx={{ flex: 1, position: 'relative', minHeight: 0, display: 'flex', flexDirection: 'column' }}>
        <Box ref={scrollContainerRef} onScroll={handleScroll} sx={{ flex: 1, overflowY: 'auto', px: 3, py: 2 }}>
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
            messages.map((msg, index) => {
              const day = msg.createdAt ? dayKey(msg.createdAt) : '';
              const previous = messages[index - 1];
              const newDay = Boolean(day) && (!previous?.createdAt || dayKey(previous.createdAt) !== day);
              return (
                <Fragment key={msg.id}>
                  {newDay && (
                    <Divider sx={{ my: 1 }}>
                      <Chip size="small" label={dayLabel(msg.createdAt!, t, i18n.language)} />
                    </Divider>
                  )}
                  <MessageBubble
                    message={msg}
                    isCurrentUser={msg.senderId === currentUserId}
                    clubName={teamClubName}
                  />
                </Fragment>
              );
            })
          )}
        </Box>
        {newCount > 0 && (
          <Chip
            color="primary"
            label={t('chat.new-messages', { count: newCount })}
            onClick={scrollToBottom}
            sx={{ position: 'absolute', bottom: 12, left: '50%', transform: 'translateX(-50%)', boxShadow: 2 }}
          />
        )}
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
          disabled={!input.trim() || sending}
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
          {sending ? <CircularProgress size={18} color="inherit" /> : <SendIcon fontSize="small" />}
        </IconButton>
      </Box>
    </Box>
  );
};
