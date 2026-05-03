import {
  createContext,
  useContext,
  useState,
  useEffect,
  type ReactNode,
} from "react";
import { useAuth as useKeycloakAuth } from "react-oidc-context";
import { type User } from "../types/auth";
import { type Role } from "../types/roles";
import { Box, CircularProgress } from "@mui/material";

interface AuthContextType {
  user: User | null;
  token: string | null;
  isLoading: boolean;
  login: () => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);


const parseJwt = (token: string) => {
  try {
    return JSON.parse(atob(token.split(".")[1]));
  } catch (e) {
    return null;
  }
};

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const auth = useKeycloakAuth();
  const [user, setUser] = useState<User | null>(null);

  useEffect(() => {
    if (auth.isLoading || auth.isAuthenticated || auth.activeNavigator) {
    return;
  }

  const hasAuthParams = new URLSearchParams(window.location.search).has("code");
  if (hasAuthParams) {
    return;
  }

  if (auth.error) {
    console.error("Autentikációs hiba történt, megállítjuk az átirányítást:", auth.error.message);
    return;
  }    
      
  if (!window.location.pathname.includes('/registration')) {
    const currentPath = window.location.pathname + window.location.search;
    if (currentPath !== '/' && currentPath !== '/login') {
      localStorage.setItem('pendingRedirectPath', currentPath);
    }
    auth.signinRedirect();
  }
    
  }, [auth.isLoading, auth.isAuthenticated, auth.activeNavigator]);

  useEffect(() => {
    if (auth.isAuthenticated && auth.user) {
      const accessToken = auth.user.access_token;
      localStorage.setItem("token", accessToken);

      const decodedToken = parseJwt(accessToken);

      const CLIENT_ID = "football-web-client";

      const resourceAccess = decodedToken?.resource_access;
      const userRoles = resourceAccess?.[CLIENT_ID]?.roles || [];

      let extractedRole = "";

      switch (true) {
        case userRoles.includes("admin"):
          extractedRole = "admin";
          break;
        case userRoles.includes("coach"):
          extractedRole = "coach";
          break;
        case userRoles.includes("player"):
          extractedRole = "player";
          break;
        default:
          extractedRole = "fan";
      }

      const profile = auth.user.profile;

      const mappedUser: User = {
        id: profile.sub,
        email: profile.email || "",
        name:
          profile.name || (profile as any).preferred_username || "Ismeretlen",
        role: extractedRole as Role,
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

  const isPublicPage = window.location.pathname.includes('/registration');
  const showApp = !auth.isLoading && (auth.isAuthenticated && user) || isPublicPage;

  return (
    <AuthContext.Provider
      value={{
        user,
        token: auth.user?.access_token || null,
        isLoading: auth.isLoading,
        login,
        logout,
      }}
    >
      {showApp ? children : (
        <Box
          sx={{
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'center',
            alignItems: 'center',
            height: '100vh',
            width: '100vw',
            backgroundColor: 'background.default',
          }}
        >
          <CircularProgress size={60} thickness={4} sx={{ mb: 3 }} />

        </Box>
      )}
    </AuthContext.Provider>
  );
};


export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error(
      "A useAuth hookot csak az AuthProvider-en belül lehet használni!",
    );
  }
  return context;
};
