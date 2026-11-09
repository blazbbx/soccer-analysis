import type { ReactNode } from 'react';
import { Link } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { FROM_CHAT_STATE } from './chatNavigation';

// Only http and https. No HTML is rendered, so no XSS and no javascript: links.
const URL_PATTERN = /\bhttps?:\/\/[^\s<>"']+/gi;
// Sentence punctuation right after a link is not part of it. A URL ending in ")" loses it (accepted).
const TRAILING_PUNCTUATION = /[.,;:!?)]+$/;

const LINK_SX = { color: 'inherit', textDecoration: 'underline', wordBreak: 'break-all' } as const;

/** The path of a link into this app (same origin), or null for an outside link. */
const appPath = (url: string): string | null => {
  try {
    const parsed = new URL(url);
    return parsed.origin === window.location.origin ? parsed.pathname + parsed.search + parsed.hash : null;
  } catch {
    return null;
  }
};

/**
 * The text with its http(s) URLs turned into links. A link into this app opens in the same tab through the
 * router: a new tab would start without the session (the token is per tab) and lose the page in the login.
 * Outside links open in a new tab.
 */
export const renderWithLinks = (text: string): ReactNode[] => {
  const parts: ReactNode[] = [];
  let last = 0;
  for (const match of text.matchAll(URL_PATTERN)) {
    const start = match.index ?? 0;
    const trailing = match[0].match(TRAILING_PUNCTUATION)?.[0] ?? '';
    const url = match[0].slice(0, match[0].length - trailing.length);
    if (start > last) parts.push(text.slice(last, start));
    const path = appPath(url);
    parts.push(path
      ? (
        <Link key={start} component={RouterLink} to={path} state={FROM_CHAT_STATE} sx={LINK_SX}>
          {url}
        </Link>
      )
      : (
        <Link key={start} href={url} target="_blank" rel="noopener noreferrer" sx={LINK_SX}>
          {url}
        </Link>
      ));
    last = start + url.length;
  }
  if (last < text.length) parts.push(text.slice(last));
  return parts;
};
