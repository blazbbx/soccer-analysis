import { useEffect, useRef, useState } from 'react';
import type { ChatMessageResponse } from '../../../api/generated/model';
import { getHistory, useGetHistory, useSendMessage } from '../../../api/generated/team-chat/team-chat';
import { useTeamChatSSE } from './useTeamChatSSE';

interface UseChatMessagesResult {
  messages: ChatMessageResponse[];
  sendMessage: (content: string) => void;
  isLoading: boolean;
  hasMore: boolean;
  loadMore: () => void;
  isLoadingMore: boolean;
}

export const useChatMessages = (teamId: string): UseChatMessagesResult => {
  const [messages, setMessages] = useState<ChatMessageResponse[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const seenIds = useRef<Set<string>>(new Set());
  const cursorRef = useRef<{ at?: string; id?: string }>({});
  const teamIdRef = useRef(teamId);
  teamIdRef.current = teamId;

  const { data: historyData, isLoading } = useGetHistory(teamId, undefined, {
    query: { enabled: !!teamId },
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

  const { mutate } = useSendMessage();

  const sendMessage = (content: string) => {
    if (!teamId || !content.trim()) return;
    mutate(
      { teamId, data: { content } },
      { onSuccess: appendMessage },
    );
  };

  const appendMessage = (msg: ChatMessageResponse) => {
    if (!msg.id || seenIds.current.has(msg.id)) return;
    if (msg.teamId && teamIdRef.current && msg.teamId !== teamIdRef.current) return;
    seenIds.current.add(msg.id);
    setMessages(prev => [...prev, msg]);
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

  useTeamChatSSE(teamId, appendMessage);

  return { messages, sendMessage, isLoading, hasMore, loadMore, isLoadingMore };
};
