import { useEffect } from 'react';
import { Container, Typography, Paper, Box, CircularProgress } from '@mui/material';
import { useAuth as useKeycloakAuth } from 'react-oidc-context';

export const Login = () => {
  const auth = useKeycloakAuth();

  useEffect(() => {
    if (!auth.isLoading && !auth.isAuthenticated) {
      auth.signinRedirect();
    }
  }, [auth.isLoading, auth.isAuthenticated, auth]);

  return (
    <Container maxWidth="sm" sx={{ display: 'flex', alignItems: 'center', minHeight: '100vh' }}>
      <Paper elevation={3} sx={{ p: 4, width: '100%', borderRadius: 2, textAlign: 'center' }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
          <CircularProgress size={50} />
          <Typography variant="h6" sx={{ color: 'text.secondary' }}>
            Redirecting to Keycloak login...
          </Typography>
        </Box>
      </Paper>
    </Container>
  );
};