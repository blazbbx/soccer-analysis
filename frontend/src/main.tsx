import "./i18n";
import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App.tsx";

import { AuthProvider as KeycloakAuthProvider } from "react-oidc-context";

import { AuthProvider } from "./context/AuthContext.tsx";
import { CustomThemeProvider } from "./context/ThemeContext.tsx";
import { TeamProvider } from "./context/TeamContext.tsx";

const oidcConfig = {
  authority: "http://localhost:8081/realms/soccer-analyzer-realm",
  client_id: "frontend-client",
  redirect_uri: "http://localhost:5173",
  post_logout_redirect_uri: "http://localhost:5173",

  automaticSilentRenew: true,
};

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <KeycloakAuthProvider {...oidcConfig}>
      <CustomThemeProvider>
        <AuthProvider>
          <TeamProvider>
            <App />
          </TeamProvider>
        </AuthProvider>
      </CustomThemeProvider>
    </KeycloakAuthProvider>
  </React.StrictMode>,
);
