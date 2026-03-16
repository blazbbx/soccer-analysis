// frontend/src/context/AuthContext.tsx
import React, { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import { type User } from '../types/auth';

interface AuthContextType {
  user: User | null;
  isLoading: boolean;
  login: (token: string, userData: User) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true); 

  // 1. Oldal betöltésekor ellenőrizzük, hogy van-e elmentett bejelentkezés
  useEffect(() => {
    const storedToken = localStorage.getItem('jwt_token');
    const storedUser = localStorage.getItem('user');

    if (storedToken && storedUser) {
      try {
        setUser(JSON.parse(storedUser));
      } catch (error) {
        console.error("Hiba a mentett felhasználó beolvasásakor", error);
        localStorage.removeItem('jwt_token');
        localStorage.removeItem('user');
      }
    }
    setIsLoading(false); // Befejeződött az ellenőrzés
  }, []);

  // 2. Bejelentkezés (A Login.tsx hívja meg a sikeres authService.login/register után)
  const login = (token: string, userData: User) => {
    setUser(userData);
    localStorage.setItem('jwt_token', token);
    localStorage.setItem('user', JSON.stringify(userData)); // Elmentjük a usert is JSON-ként
  };

  // 3. Kijelentkezés
  const logout = () => {
    setUser(null);
    localStorage.removeItem('jwt_token');
    localStorage.removeItem('user');
  };

  return (
    <AuthContext.Provider value={{ user, isLoading, login, logout }}>
      {/* Csak akkor rendereljük a gyerekeket, ha már tudjuk az auth státuszt */}
      {!isLoading && children}
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