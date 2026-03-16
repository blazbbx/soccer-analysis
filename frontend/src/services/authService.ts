import { User } from '../types/auth';
import {MockAuthService} from '../services/MockAuthService';

export interface IAuthService {
  login(email: string, password?: string): Promise<{ user: User; token: string }>;
  logout(): Promise<void>;
}

export const authService = new MockAuthService();