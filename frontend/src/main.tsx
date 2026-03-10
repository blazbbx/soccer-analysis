import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.tsx';

import { AuthProvider } from './context/AuthContext.tsx';
import { CustomThemeProvider } from './context/ThemeContext.tsx';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <CustomThemeProvider>         
      <AuthProvider>
        <App />
      </AuthProvider>      
    </CustomThemeProvider>
  </React.StrictMode>,
);