import { createContext, useState, useMemo, useContext, type ReactNode, useEffect } from 'react';
import { ThemeProvider} from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';

import { lightTheme, darkTheme } from '../theme/theme';

type ColorModeContextType = {
  toggleColorMode: () => void;
  mode: 'light' | 'dark'; 
};

const ColorModeContext = createContext<ColorModeContextType>({
  toggleColorMode: () => {},
  mode: 'light',
});

export const useColorMode = () => {
  return useContext(ColorModeContext);
};

export const CustomThemeProvider = ({ children }: {children:ReactNode}) => {
  const [mode, setMode] = useState<'light' | 'dark'>(() => {
    const savedMode = localStorage.getItem('app-theme-mode');
    return (savedMode === 'dark' || savedMode === 'light') ? savedMode : 'light';
  });

  useEffect(() => {
    localStorage.setItem('app-theme-mode', mode);
  }, [mode]);

  const colorMode = useMemo(
    () => ({
      toggleColorMode: () => {
        setMode((prevMode) => (prevMode === 'light' ? 'dark' : 'light'));
      },
      mode,
    }),
    [mode]
  );

  const theme = mode === 'light' ? lightTheme : darkTheme;

  return (
    <ColorModeContext.Provider value={colorMode}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ColorModeContext.Provider>
  );
};