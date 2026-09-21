import { useEffect, useRef } from 'react';
import type { ChatMessageResponse } from '../../../api/generated/model';
import { getApiBaseUrl } from '../../../api/apiBase';

const MAX_BACKOFF_MS = 30_000;
const CHAT_EVENT = 'TEAM_CHAT_MESSAGE';

export const useTeamChatSSE = (teamId: string, onMessage: (msg: ChatMessageResponse) => void): void => {
  const onMessageRef = useRef(onMessage);
  onMessageRef.current = onMessage;

  useEffect(() => {
    if (!teamId) return;

    const abortController = new AbortController();
    let retryDelay = 1_000;
    let stopped = false;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;

    const connect = async () => {
      const token = localStorage.getItem('token');

      try {
        const response = await fetch(`${getApiBaseUrl()}/api/teams/${teamId}/chat/subscribe`, {
          headers: {
            Accept: 'text/event-stream',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
          signal: abortController.signal,
        });

        if (!response.ok || !response.body) {
          throw new Error(`SSE connect failed: ${response.status}`);
        }

        retryDelay = 1_000;

        const reader = response.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          let separatorIndex: number;
          while ((separatorIndex = buffer.search(/\r?\n\r?\n/)) >= 0) {
            const rawMessage = buffer.slice(0, separatorIndex);
            buffer = buffer.slice(separatorIndex).replace(/^\r?\n\r?\n/, '');
            const parsed = parseSseMessage(rawMessage);
            if (!parsed || parsed.event === 'INIT' || parsed.data === 'ping') continue;
            if (parsed.event !== CHAT_EVENT && parsed.event !== 'message') continue;
            try {
              const msg = JSON.parse(parsed.data) as ChatMessageResponse;
              if (msg?.id) onMessageRef.current(msg);
            } catch {
              // ignore malformed event
            }
          }
        }
      } catch (err) {
        if ((err as { name?: string }).name === 'AbortError') return;
      }

      if (!stopped) {
        retryTimer = setTimeout(connect, retryDelay);
        retryDelay = Math.min(retryDelay * 2, MAX_BACKOFF_MS);
      }
    };

    connect();

    return () => {
      stopped = true;
      clearTimeout(retryTimer);
      abortController.abort();
    };
  }, [teamId]);
};

const parseSseMessage = (raw: string): { event: string; data: string } | null => {
  let event = 'message';
  const dataLines: string[] = [];
  for (const line of raw.split(/\r?\n/)) {
    if (!line || line.startsWith(':')) continue;
    const colon = line.indexOf(':');
    const field = colon === -1 ? line : line.slice(0, colon);
    const value = colon === -1 ? '' : line.slice(colon + 1).replace(/^ /, '');
    if (field === 'event') event = value;
    else if (field === 'data') dataLines.push(value);
  }
  if (dataLines.length === 0) return null;
  return { event, data: dataLines.join('\n') };
};
