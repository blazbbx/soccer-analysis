/**
 * Minimal Server-Sent Events client built on `fetch` + ReadableStream.
 *
 * The native `EventSource` API does not support custom headers, so we cannot use it
 * to pass the JWT Authorization header that the backend requires. This implementation
 * keeps the same event-name + data callback shape so callers feel idiomatic.
 */

import { getApiBaseUrl } from '../api/apiBase';

export interface MatchEventStreamOptions {
  onEvent: (eventName: string, data: string) => void;
  onError?: (err: unknown) => void;
}

export interface MatchEventStreamHandle {
  close: () => void;
}

export const openMatchEventStream = (
  matchId: string,
  { onEvent, onError }: MatchEventStreamOptions,
): MatchEventStreamHandle => {
  const controller = new AbortController();
  const token = localStorage.getItem('token');

  const headers: Record<string, string> = {
    Accept: 'text/event-stream',
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  void (async () => {
    try {
      const response = await fetch(
        `${getApiBaseUrl()}/api/notifications/subscribe/${matchId}`,
        {
          method: 'GET',
          headers,
          signal: controller.signal,
        },
      );

      if (!response.ok || !response.body) {
        throw new Error(`SSE connect failed: ${response.status}`);
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';

      while (true) {
        const { value, done } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        // SSE messages are separated by a blank line ("\n\n" or "\r\n\r\n").
        let separatorIndex: number;
        while ((separatorIndex = buffer.search(/\r?\n\r?\n/)) >= 0) {
          const rawMessage = buffer.slice(0, separatorIndex);
          buffer = buffer.slice(separatorIndex).replace(/^\r?\n\r?\n/, '');
          const parsed = parseSseMessage(rawMessage);
          if (parsed) onEvent(parsed.event, parsed.data);
        }
      }
    } catch (err) {
      // AbortError is expected when the caller closes the stream.
      if ((err as { name?: string })?.name === 'AbortError') return;
      onError?.(err);
    }
  })();

  return { close: () => controller.abort() };
};

const parseSseMessage = (raw: string): { event: string; data: string } | null => {
  let event = 'message';
  const dataLines: string[] = [];
  for (const line of raw.split(/\r?\n/)) {
    if (!line || line.startsWith(':')) continue; // empty line or comment
    const colon = line.indexOf(':');
    const field = colon === -1 ? line : line.slice(0, colon);
    const value = colon === -1 ? '' : line.slice(colon + 1).replace(/^ /, '');
    if (field === 'event') event = value;
    else if (field === 'data') dataLines.push(value);
  }
  if (dataLines.length === 0 && event === 'message') return null;
  return { event, data: dataLines.join('\n') };
};
