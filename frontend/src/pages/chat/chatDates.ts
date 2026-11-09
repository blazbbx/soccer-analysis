import type { TFunction } from 'i18next';

const pad = (n: number) => String(n).padStart(2, '0');

const keyOf = (date: Date) => `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;

/** Local calendar day of a message time (createdAt is a local date-time without zone), as "yyyy-mm-dd". */
export const dayKey = (iso: string): string => keyOf(new Date(iso));

/** Day separator label: "Today", "Yesterday", month + day this year, with the year before that. */
export const dayLabel = (iso: string, t: TFunction, locale: string): string => {
  const date = new Date(iso);
  const today = new Date();
  const yesterday = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1);
  const key = keyOf(date);
  if (key === keyOf(today)) return t('chat.today');
  if (key === keyOf(yesterday)) return t('chat.yesterday');
  return date.toLocaleDateString(locale, date.getFullYear() === today.getFullYear()
    ? { month: 'long', day: 'numeric' }
    : { year: 'numeric', month: 'long', day: 'numeric' });
};
