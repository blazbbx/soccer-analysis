import "./i18n";
import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App.tsx";

import { AuthProvider as KeycloakAuthProvider } from "react-oidc-context";

import { AuthProvider } from "./context/AuthContext.tsx";
import { CustomThemeProvider } from "./context/ThemeContext.tsx";
import { getKeycloakAuthority } from "./api/apiBase";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const frontendUrl = import.meta.env.VITE_FRONTEND_URL || window.location.origin;

const oidcConfig = {
  authority: getKeycloakAuthority(),
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
              <App />            
          </AuthProvider>
        </QueryClientProvider>
      </CustomThemeProvider>
    </KeycloakAuthProvider>
  </React.StrictMode>,
);
