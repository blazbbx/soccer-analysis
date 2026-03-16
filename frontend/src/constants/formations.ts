export const FORMATIONS = [
  '2-1-1',
  '2-2',
  '3-1',
  '1-1-2',
  '3-1'
] as const;

export type FormationType = typeof FORMATIONS[number];