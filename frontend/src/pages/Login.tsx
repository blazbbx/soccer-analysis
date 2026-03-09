import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { 
  Box, 
  Button, 
  Container, 
  Paper, 
  TextField, 
  Typography, 
  Alert, 
  CircularProgress
} from '@mui/material';

export default function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  
  const { login, isLoading } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    
    try {
      await login(email, password);
      navigate('/');
    } catch (err) {
      setError('Hibás felhasználónév vagy jelszó!');
    }
  };

  return (
    // Teljes képernyős, középre igazított konténer
    <Box sx={{ minHeight: '100vh', display: 'flex', alignItems: 'center', bgcolor: 'background.default' }}>
      <Container maxWidth="sm">
        <Paper elevation={3} sx={{ p: 5, borderRadius: 2 }}>
          <Typography variant="h4" align="center" gutterBottom fontWeight="bold" color="primary">
            Soccer Analyzer
          </Typography>
          <Typography variant="body1" align="center" color="text.secondary" sx={{ mb: 4 }}>
            Jelentkezz be a folytatáshoz
          </Typography>

          {/* Hibaüzenet megjelenítése, ha van */}
          {error && <Alert severity="error" sx={{ mb: 3 }}>{error}</Alert>}

          <form onSubmit={handleSubmit}>
            <TextField
              fullWidth
              label="Email cím"
              variant="outlined"
              margin="normal"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="pl. coach@gmail.com"
              disabled={isLoading}
              required
            />
            <TextField
              fullWidth
              label="Jelszó"
              type="password"
              variant="outlined"
              margin="normal"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              disabled={isLoading}
            />
            <Button
              type="submit"
              fullWidth
              variant="contained"
              size="large"
              disabled={isLoading}
              sx={{ mt: 3, mb: 2, height: 48 }}
            >
              {isLoading ? <CircularProgress size={24} color="inherit" /> : 'Bejelentkezés'}
            </Button>
          </form>
        </Paper>
      </Container>
    </Box>
  );
}