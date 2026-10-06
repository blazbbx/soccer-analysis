export const ROLES = {
  ADMIN: 'admin',
  CLUB_ADMIN: 'club_admin',
  COACH: 'coach',
  PLAYER: 'player',
  FAN: 'fan',
  PARENT: 'parent',
} as const;

export type Role = typeof ROLES[keyof typeof ROLES];

export const HAT_RANK: Role[] = [
  ROLES.ADMIN,
  ROLES.CLUB_ADMIN,
  ROLES.COACH,
  ROLES.PLAYER,
  ROLES.PARENT,
  ROLES.FAN,
];