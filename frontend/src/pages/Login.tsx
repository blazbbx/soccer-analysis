import { Container, Typography, Button, Paper } from '@mui/material';
import { useAuth } from '../context/AuthContext';
import { Navigate } from 'react-router-dom';

export const Login = () => {
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