import { useEffect, useRef } from 'react';
import type { ChatMessageResponse } from '../../../api/generated/model';

const BASE_URL = 'http://localhost:8080';
const SSE_URL = `${BASE_URL}/api/teams/chat/subscribe`;
const MAX_BACKOFF_MS = 30_000;

export const useTeamChatSSE = (onMessage: (msg: ChatMessageResponse) => void): void => {
  const onMessageRef = useRef(onMessage);
  onMessageRef.current = onMessage;

  useEffect(() => {
    let abortController = new AbortController();
    let retryDelay = 1_000;
    let stopped = false;

    const connect = async () => {
      const token = localStorage.getItem('token');

      try {
        const response = await fetch(SSE_URL, {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
          signal: abortController.signal,
        });

        if (!response.ok || !response.body) {
          throw new Error(`SSE connect failed: ${response.status}`);
        }

        retryDelay = 1_000;

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() ?? '';

          for (const line of lines) {
            if (line.startsWith('data:')) {
              const raw = line.slice(5).trim();
              if (!raw || raw === 'ping') continue;
              try {
                const msg = JSON.parse(raw) as ChatMessageResponse;
                onMessageRef.current(msg);
              } catch {
                // ignore malformed event
              }
            }
          }
        }
      } catch (err) {
        if ((err as { name?: string }).name === 'AbortError') return;
        // intentionally not logging in prod
      }

      if (!stopped) {
        setTimeout(connect, retryDelay);
        retryDelay = Math.min(retryDelay * 2, MAX_BACKOFF_MS);
      }
    };

    connect();

    return () => {
      stopped = true;
      abortController.abort();
    };
  }, []);
};
