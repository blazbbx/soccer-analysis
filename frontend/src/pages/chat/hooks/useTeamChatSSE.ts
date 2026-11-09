import { useEffect, useRef } from 'react';
import type { ChatMessageResponse } from '../../../api/generated/model';
import { getApiBaseUrl } from '../../../api/apiBase';
import { handleUnauthorized } from '../../../api/axiosInstance';

const MAX_BACKOFF_MS = 30_000;
// The server sends ": ping" every 25 s. With no data for this long the connection is dead.
const WATCHDOG_MS = 60_000;
const CHAT_EVENT = 'TEAM_CHAT_MESSAGE';
const ACCESS_REVOKED_EVENT = 'ACCESS_REVOKED';

/**
 * Live messages of the open team chat. Reconnects with backoff when the connection drops or goes
 * silent, and calls onConnected after every (re)connect, so missed messages can be fetched.
 * Stops for good on 401 (logs out), 403 and ACCESS_REVOKED (the user was removed from the team).
 */
export const useTeamChatSSE = (
  teamId: string,
  onMessage: (msg: ChatMessageResponse) => void,
  onConnected?: () => void,
  onAccessRevoked?: () => void,
): void => {
  const onMessageRef = useRef(onMessage);
  onMessageRef.current = onMessage;
  const onConnectedRef = useRef(onConnected);
  onConnectedRef.current = onConnected;
  const onAccessRevokedRef = useRef(onAccessRevoked);
  onAccessRevokedRef.current = onAccessRevoked;

  useEffect(() => {
    if (!teamId) return;

    let retryDelay = 1_000;
    let stopped = false;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;
    let connection: AbortController | undefined;

    const connect = async () => {
      const token = sessionStorage.getItem('token');
      const authorization = token ? `Bearer ${token}` : undefined;
      const current = new AbortController();
      connection = current;
      let watchdog: ReturnType<typeof setTimeout> | undefined;
      const resetWatchdog = () => {
        clearTimeout(watchdog);
        watchdog = setTimeout(() => current.abort(), WATCHDOG_MS);
      };

      try {
        resetWatchdog();
        const response = await fetch(`${getApiBaseUrl()}/api/teams/${teamId}/chat/subscribe`, {
          headers: {
            Accept: 'text/event-stream',
            ...(authorization ? { Authorization: authorization } : {}),
          },
          signal: current.signal,
        });

        // The raw fetch skips the axios interceptor, so the 401 logout is called here.
        if (response.status === 401 || response.status === 403) {
          if (response.status === 401) handleUnauthorized(authorization);
          return;
        }
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
          resetWatchdog();

          buffer += decoder.decode(value, { stream: true });
          let separatorIndex: number;
          while ((separatorIndex = buffer.search(/\r?\n\r?\n/)) >= 0) {
            const rawMessage = buffer.slice(0, separatorIndex);
            buffer = buffer.slice(separatorIndex).replace(/^\r?\n\r?\n/, '');
            const parsed = parseSseMessage(rawMessage);
            if (!parsed || parsed.data === 'ping') continue;
            if (parsed.event === 'INIT') {
              onConnectedRef.current?.();
              continue;
            }
            if (parsed.event === ACCESS_REVOKED_EVENT) {
              stopped = true;
              onAccessRevokedRef.current?.();
              return;
            }
            if (parsed.event !== CHAT_EVENT && parsed.event !== 'message') continue;
            try {
              const msg = JSON.parse(parsed.data) as ChatMessageResponse;
              if (msg?.id) onMessageRef.current(msg);
            } catch {
              // ignore malformed event
            }
          }
        }
      } catch {
        // Network error, or the watchdog / unmount aborted the connection.
      } finally {
        clearTimeout(watchdog);
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
      connection?.abort();
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
