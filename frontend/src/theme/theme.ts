import { createTheme } from '@mui/material/styles';

const commonSettings = {
  typography: {
    fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
    h1: { fontSize: '1.5rem', fontWeight: 500, lineHeight: 1.5 },
    h2: { fontSize: '1.25rem', fontWeight: 500, lineHeight: 1.5 },
    h3: { fontSize: '1.125rem', fontWeight: 500, lineHeight: 1.5 },
    h4: { fontSize: '1rem', fontWeight: 500, lineHeight: 1.5 },
    button: { 
      fontSize: '1rem', 
      fontWeight: 500, 
      lineHeight: 1.5, 
      textTransform: 'none' as const 
    },
  },
  shape: {    
    borderRadius: 10,
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          boxShadow: 'none',
          '&:hover': {
            boxShadow: 'none',
          },
        },
      },
    },
  },
};


export const lightTheme = createTheme({
  ...commonSettings,
  palette: {
    mode: 'light',
    background: {
      default: '#ffffff', 
      paper: '#ffffff',   
    },
    text: {
      primary: '#09090b', 
      secondary: '#717182', 
    },
    primary: {
      main: '#030213',     
      contrastText: '#ffffff', 
    },
    secondary: {
      main: '#f4f4f5',    
      contrastText: '#030213', 
    },
    error: {
      main: '#d4183d',          
      contrastText: '#ffffff',  
    },
    divider: '#e4e4e7',
  },
});


export const darkTheme = createTheme({
  ...commonSettings,
  palette: {
    mode: 'dark',
    background: {
      default: '#09090b', 
      paper: '#09090b',   
    },
    text: {
      primary: '#fafafa', 
      secondary: '#a1a1aa', 
    },
    primary: {
      main: '#fafafa',     
      contrastText: '#18181b', 
    },
    secondary: {
      main: '#27272a',    
      contrastText: '#fafafa', 
    },
    error: {
      main: '#7f1d1d',
      contrastText: '#ef4444', 
    },
    divider: '#27272a',
  },
});