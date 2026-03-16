/* eslint-disable react-refresh/only-export-components */
import React, { createContext, useContext, useState, type ReactNode } from 'react';
import { type User } from '../types/auth';
import { authService } from '../services/authService'; 

interface AuthContextType {
  user: User | null; 
  isLoading: boolean; 
  login: (email: string, password?: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);

 const login = async (email: string, password?: string) => {
  setIsLoading(true);
  try {    
    const data = await authService.login(email, password);
    
    setUser(data.user);
    localStorage.setItem('jwt_token', data.token); 
  } catch (error) {
    console.error("Hiba a bejelentkezéskor", error);
    throw error;
  } finally {
    setIsLoading(false);
  }
};

 
  const logout = async () => {
    setIsLoading(true);
    try {
      await authService.logout();
      setUser(null); 
    } finally {
      setIsLoading(false);
    }
  };

 
  return (
    <AuthContext.Provider value={{ user, isLoading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('A useAuth hookot csak az AuthProvider-en belül lehet használni!');
  }
  return context;
};