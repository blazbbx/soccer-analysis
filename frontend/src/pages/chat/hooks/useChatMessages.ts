import { useEffect, useRef, useState } from 'react';
import type { ChatMessageResponse } from '../../../api/generated/model';
import { useQueryClient } from '@tanstack/react-query';
import {
  getGetSharedClipsQueryKey,
  getHistory,
  useGetHistory,
  useSendMessage,
} from '../../../api/generated/team-chat/team-chat';
import { useTeamChatSSE } from './useTeamChatSSE';

interface UseChatMessagesResult {
  messages: ChatMessageResponse[];
  sendMessage: (content: string) => Promise<void>;
  isLoading: boolean;
  hasMore: boolean;
  loadMore: () => void;
  isLoadingMore: boolean;
}

export const useChatMessages = (teamId: string, onAccessRevoked?: () => void): UseChatMessagesResult => {
  const [messages, setMessages] = useState<ChatMessageResponse[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const seenIds = useRef<Set<string>>(new Set());
  const cursorRef = useRef<{ at?: string; id?: string }>({});
  const teamIdRef = useRef(teamId);
  teamIdRef.current = teamId;
  const queryClient = useQueryClient();

  // A new clip message changes the shared clips section of the left panel.
  const refreshSharedClips = (msgs: ChatMessageResponse[]) => {
    if (teamIdRef.current && msgs.some(m => m.type === 'CLIP')) {
      queryClient.invalidateQueries({ queryKey: getGetSharedClipsQueryKey(teamIdRef.current) });
    }
  };

  const messagesRef = useRef(messages);
  messagesRef.current = messages;

  // No automatic refetch: it would replace the list with the latest 50 and drop the older pages.
  // Missed messages are fetched by catchUp after every SSE (re)connect.
  const { data: historyData, isLoading, refetch } = useGetHistory(teamId, undefined, {
    query: { enabled: !!teamId, refetchOnWindowFocus: false, staleTime: Infinity },
  });

  useEffect(() => {
    seenIds.current = new Set();
    cursorRef.current = {};
    setHasMore(false);
    if (!historyData?.messages) {
      setMessages([]);
      return;
    }
    seenIds.current = new Set(historyData.messages.map(m => m.id ?? '').filter(Boolean));
    setMessages(historyData.messages);
    setHasMore(historyData.hasMore ?? false);
    cursorRef.current = {
      at: historyData.nextBeforeCreatedAt,
      id: historyData.nextBeforeId,
    };
  }, [historyData, teamId]);

  const { mutateAsync } = useSendMessage();

  // Resolves after the server saved the message; rejects on error, so the caller can keep the text.
  const sendMessage = async (content: string) => {
    if (!teamId || !content.trim()) return;
    const saved = await mutateAsync({ teamId, data: { content } });
    appendMessage(saved);
  };

  const appendMessage = (msg: ChatMessageResponse) => {
    if (!msg.id || seenIds.current.has(msg.id)) return;
    if (msg.teamId && teamIdRef.current && msg.teamId !== teamIdRef.current) return;
    seenIds.current.add(msg.id);
    setMessages(prev => [...prev, msg]);
    refreshSharedClips([msg]);
  };

  const loadMore = async () => {
    if (!teamId || !cursorRef.current.at || isLoadingMore) return;
    setIsLoadingMore(true);
    try {
      const older = await getHistory(teamId, {
        beforeCreatedAt: cursorRef.current.at,
        beforeId: cursorRef.current.id,
        limit: 50,
      });
      const newMsgs = (older.messages ?? []).filter(m => m.id && !seenIds.current.has(m.id));
      newMsgs.forEach(m => seenIds.current.add(m.id!));
      setMessages(prev => [...newMsgs, ...prev]);
      setHasMore(older.hasMore ?? false);
      cursorRef.current = {
        at: older.nextBeforeCreatedAt,
        id: older.nextBeforeId,
      };
    } finally {
      setIsLoadingMore(false);
    }
  };

  // Fetches the messages sent after the last one in the list (called after every SSE connect).
  const catchUp = async () => {
    const requestedTeamId = teamIdRef.current;
    const last = messagesRef.current[messagesRef.current.length - 1];
    if (!requestedTeamId || !last?.createdAt || !last.id) return;
    try {
      const newer = await getHistory(requestedTeamId, {
        afterCreatedAt: last.createdAt,
        afterId: last.id,
        limit: 50,
      });
      if (teamIdRef.current !== requestedTeamId) return;
      if (newer.hasMore) {
        // More than 50 were missed: load the latest page again.
        refetch();
        return;
      }
      const fresh = (newer.messages ?? []).filter(m => m.id && !seenIds.current.has(m.id));
      if (fresh.length === 0) return;
      fresh.forEach(m => seenIds.current.add(m.id!));
      // A live SSE message may have arrived before the caught-up ones.
      setMessages(prev => [...prev, ...fresh].sort(byCreatedAtThenId));
      refreshSharedClips(fresh);
    } catch {
      // The next reconnect tries again.
    }
  };

  useTeamChatSSE(teamId, appendMessage, catchUp, onAccessRevoked);

  return { messages, sendMessage, isLoading, hasMore, loadMore, isLoadingMore };
};

// createdAt is an ISO local date-time string, so string order is time order.
const byCreatedAtThenId = (a: ChatMessageResponse, b: ChatMessageResponse): number =>
  (a.createdAt ?? '').localeCompare(b.createdAt ?? '') || (a.id ?? '').localeCompare(b.id ?? '');
