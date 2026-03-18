import React from 'react';
import { Box, Container, Typography, Button, Paper } from '@mui/material';
import { useAuth } from '../context/AuthContext';
import { useTranslation } from 'react-i18next';
import { Navigate } from 'react-router-dom';

export const Login = () => {
  const { t } = useTranslation();
  const { login, isLoading, user } = useAuth(); 

  if (user) {
    return <Navigate to="/" replace />; 
  }

  return (
    <Container maxWidth="sm" sx={{ display: 'flex', alignItems: 'center', minHeight: '100vh' }}>
      <Paper elevation={3} sx={{ p: 4, width: '100%', borderRadius: 2, textAlign: 'center' }}>
        
        <Typography variant="h4" gutterBottom sx={{ fontWeight: 'bold' }}>
          Üdvözlünk a Soccer Analysis-ben!
        </Typography>
        
        <Typography variant="body1" sx={{ mb: 4, color: 'text.secondary' }}>
          A bejelentkezéshez és regisztrációhoz kérjük, használd a biztonságos központi rendszert.
        </Typography>

        <Button 
          onClick={login} 
          variant="contained" 
          size="large" 
          disabled={isLoading}
          sx={{ py: 1.5, px: 4 }}
        >
          {isLoading ? "Betöltés..." : "Bejelentkezés / Regisztráció"}
        </Button>

      </Paper>
    </Container>
  );
};