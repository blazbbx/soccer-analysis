export const formatTime = (seconds: number): string => {
  if (!Number.isFinite(seconds)) return '00:00';
  const m = Math.floor(seconds / 60)
    .toString()
    .padStart(2, '0');
  const s = Math.floor(seconds % 60)
    .toString()
    .padStart(2, '0');
  return `${m}:${s}`;
};
