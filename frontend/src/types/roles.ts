export const ROLES = {
  ADMIN: 'admin',
  CLUB_ADMIN: 'club_admin',
  COACH: 'coach',
  PLAYER: 'player',
  FAN: 'fan',
  PARENT: 'parent',
} as const;

export type Role = typeof ROLES[keyof typeof ROLES];