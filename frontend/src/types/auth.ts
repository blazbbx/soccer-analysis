export type Role = 'coach' | 'player' | 'admin' | 'fan';

export interface User {
  id: string;
  email: string;
  name: string;
  role: Role;
}