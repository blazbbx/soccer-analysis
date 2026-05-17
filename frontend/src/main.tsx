import "./i18n";
import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App.tsx";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";

import { AuthProvider as KeycloakAuthProvider } from "react-oidc-context";

import { AuthProvider } from "./context/AuthContext.tsx";
import { CustomThemeProvider } from "./context/ThemeContext.tsx";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const frontendUrl = import.meta.env.VITE_FRONTEND_URL || window.location.origin;

const oidcConfig = {
  authority: import.meta.env.VITE_KEYCLOAK_AUTHORITY || "http://localhost:9080/realms/football-realm",
  client_id: "football-web-client",
  redirect_uri: frontendUrl,
  post_logout_redirect_uri: frontendUrl,

  automaticSilentRenew: true,

  onSigninCallback: () => {
    const pendingPath = localStorage.getItem('pendingRedirectPath');
    localStorage.removeItem('pendingRedirectPath');
    window.history.replaceState(
      {},
      document.title,
      pendingPath || '/'
    );
  }
};

const queryClient = new QueryClient();

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <KeycloakAuthProvider {...oidcConfig}>
      <CustomThemeProvider>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <LocalizationProvider dateAdapter={AdapterDayjs}>
              <App />
            </LocalizationProvider>
          </AuthProvider>
        </QueryClientProvider>
      </CustomThemeProvider>
    </KeycloakAuthProvider>
  </React.StrictMode>,
);
