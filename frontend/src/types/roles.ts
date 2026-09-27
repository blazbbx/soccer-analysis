export const ROLES = {
  ADMIN: 'admin',
  COACH: 'coach',
  PLAYER: 'player',
  FAN: 'fan',
  PARENT: 'parent',
} as const;

export type Role = typeof ROLES[keyof typeof ROLES];