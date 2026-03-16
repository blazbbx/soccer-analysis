import React, { useState } from 'react';
import { 
  Box, 
  Container, 
  Typography, 
  TextField, 
  Button, 
  Paper, 
  Alert,
  MenuItem,
  Link
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { authService } from '../services/authService';
import { ROLES } from '../types/roles';
import { useTranslation } from 'react-i18next';

export const Login = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { login } = useAuth(); // Feltételezem, hogy az AuthContext-ben van egy login fgv. ami beállítja a usert

  const [isRegisterMode, setIsRegisterMode] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  // Form adatok
  const [formData, setFormData] = useState({
    name: '',
    email: '',
    password: '',
    role: ROLES.PLAYER // Alapértelmezett szerepkör regisztrációkor
  });

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      let response;
      if (isRegisterMode) {
        // Regisztráció hívása
        response = await authService.register(formData);
      } else {
        // Bejelentkezés hívása (csak email és jelszó kell)
        response = await authService.login({ email: formData.email, password: formData.password });
      }

      // Siker esetén beállítjuk a usert a contextben és átirányítjuk
      login(response.token, response.user); 
      navigate('/dashboard'); // Vagy amire az alapértelmezett oldalad be van állítva

    } catch (err: any) {
      setError(err.message || "Hiba történt a folyamat során.");
    } finally {
      setLoading(false);
    }
  };

  // Váltás a módok között (törli az eddig beírt adatokat és hibákat is)
  const toggleMode = () => {
    setIsRegisterMode(!isRegisterMode);
    setError(null);
    setFormData({ name: '', email: '', password: '', role: ROLES.PLAYER });
  };

  return (
    <Container maxWidth="sm" sx={{ display: 'flex', alignItems: 'center', minHeight: '100vh' }}>
      <Paper elevation={3} sx={{ p: 4, width: '100%', borderRadius: 2 }}>
        <Typography variant="h4" align="center" gutterBottom sx={{ fontWeight: 'bold' }}>
          {isRegisterMode ? "Regisztráció" : "Bejelentkezés"}
        </Typography>

        {error && <Alert severity="error" sx={{ mb: 3 }}>{error}</Alert>}

        <form onSubmit={handleSubmit}>
          <Box display="flex" flexDirection="column" gap={3}>
            
            {/* Regisztráció esetén plusz mezők */}
            {isRegisterMode && (
              <>
                <TextField
                  label="Teljes Név"
                  name="name"
                  value={formData.name}
                  onChange={handleChange}
                  required
                  fullWidth
                />
                
                <TextField
                  select
                  label="Szerepkör"
                  name="role"
                  value={formData.role}
                  onChange={handleChange}
                  fullWidth
                >
                  <MenuItem value={ROLES.PLAYER}>Játékos</MenuItem>
                  <MenuItem value={ROLES.COACH}>Edző</MenuItem>
                  <MenuItem value={ROLES.FAN}>Szurkoló</MenuItem>
                </TextField>
              </>
            )}

            {/* Mindkét esetben használt mezők */}
            <TextField
              label="E-mail cím"
              type="email"
              name="email"
              value={formData.email}
              onChange={handleChange}
              required
              fullWidth
            />
            
            <TextField
              label="Jelszó"
              type="password"
              name="password"
              value={formData.password}
              onChange={handleChange}
              required
              fullWidth
            />

            <Button 
              type="submit" 
              variant="contained" 
              size="large" 
              disabled={loading}
              sx={{ mt: 2, py: 1.5 }}
            >
              {loading 
                ? "Kérjük várjon..." 
                : (isRegisterMode ? "Fiók Létrehozása" : "Bejelentkezés")
              }
            </Button>
          </Box>
        </form>

        <Box textAlign="center" mt={3}>
          <Typography variant="body2">
            {isRegisterMode ? "Már van fiókod? " : "Nincs még fiókod? "}
            <Link component="button" variant="body2" onClick={toggleMode}>
              {isRegisterMode ? "Jelentkezz be itt" : "Regisztrálj itt"}
            </Link>
          </Typography>
        </Box>
      </Paper>
    </Container>
  );
};