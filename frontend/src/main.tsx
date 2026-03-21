import "./i18n";
import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App.tsx";

import { AuthProvider as KeycloakAuthProvider } from "react-oidc-context";

import { AuthProvider } from "./context/AuthContext.tsx";
import { CustomThemeProvider } from "./context/ThemeContext.tsx";
import { TeamProvider } from "./context/TeamContext.tsx";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

const oidcConfig = {
  authority: "http://localhost:9080/realms/football-realm",
  client_id: "football-web-client",
  redirect_uri: "http://localhost:5173",
  post_logout_redirect_uri: "http://localhost:5173",

  automaticSilentRenew: true,
};

const queryClient = new QueryClient();

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <KeycloakAuthProvider {...oidcConfig}>
      <CustomThemeProvider>
        <QueryClientProvider client={queryClient}>
          <AuthProvider>
            <TeamProvider>
              <App />
            </TeamProvider>
          </AuthProvider>
        </QueryClientProvider>
      </CustomThemeProvider>
    </KeycloakAuthProvider>
  </React.StrictMode>,
);
