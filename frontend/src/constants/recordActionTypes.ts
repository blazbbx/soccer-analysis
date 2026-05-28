export const RECORD_ACTION_TYPES = {
  PLAY: 'PLAY',
  PAUSE: 'PAUSE',
  SEEK: 'SEEK',
} as const;

export type RecordActionType = (typeof RECORD_ACTION_TYPES)[keyof typeof RECORD_ACTION_TYPES];
