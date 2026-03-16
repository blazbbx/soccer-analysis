import "./i18n";
import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App.tsx";

import { AuthProvider } from "./context/AuthContext.tsx";
import { CustomThemeProvider } from "./context/ThemeContext.tsx";
import { TeamProvider } from "./context/TeamContext.tsx";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <CustomThemeProvider>
      <AuthProvider>
        <TeamProvider>
          <App />
        </TeamProvider>
      </AuthProvider>
    </CustomThemeProvider>
  </React.StrictMode>,
);
