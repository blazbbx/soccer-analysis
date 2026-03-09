export const ROLES = {
  ADMIN: 'admin',
  COACH: 'coach',
  PLAYER: 'player',
  FAN: 'fan',
} as const;

export type Role = typeof ROLES[keyof typeof ROLES];