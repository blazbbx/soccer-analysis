// frontend/src/types/auth.ts

export interface User {
  id: string;
  email: string;
  name: string;
  role: string;
}

export interface LoginCredentials {
  email: string;
  password?: string;
}

export interface RegisterData {
  name: string;
  email: string;
  password?: string;
  role: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}