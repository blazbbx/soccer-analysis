import React, { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import { useAuth as useKeycloakAuth } from 'react-oidc-context';
import { type User } from '../types/auth';
import { Role } from '../types/roles';

interface AuthContextType {
  user: User | null;
  isLoading: boolean;
  login: () => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const auth = useKeycloakAuth();
  const [user, setUser] = useState<User | null>(null);

  useEffect(() => {
    if (auth.isAuthenticated && auth.user) {
      const profile = auth.user.profile;

      const realmAccess = profile.realm_access as { roles?: string[] } | undefined;
      const userRoles = realmAccess?.roles || [];     

      let extractedRole = ''

      switch(true){ 
        case userRoles.includes('admin'):
          extractedRole = 'admin';
          break;
        case userRoles.includes('coach'):
          extractedRole = 'coach';
          break;
        case userRoles.includes('player'):
          extractedRole = 'player';
          break;
        default:
          extractedRole = 'fan';
      }      
                      
      
      const mappedUser: User = {
        id: profile.sub,
        email: profile.email || '',
        name: profile.name || (profile as any).preferred_username ||"Ismeretlen",       
        role: extractedRole as Role
      };
      
      setUser(mappedUser);
    } else {
      setUser(null);
    }
  }, [auth.isAuthenticated, auth.user]);

  const login = () => {
    auth.signinRedirect();
  };

  const logout = () => {
    auth.signoutRedirect();
  };

  return (
    <AuthContext.Provider value={{ user, isLoading: auth.isLoading, login, logout }}>
      {!auth.isLoading && children}
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