import { useState } from 'react';
import {
  Container,
  Paper,
  TextField,
  Button,
  Typography,
  Box,
  Alert,
} from '@mui/material';
import { useSearchParams } from 'react-router-dom';
import { useAuth as useKeycloakAuth } from 'react-oidc-context';
import { useTranslation } from 'react-i18next';
import { useRegister } from '../../api/generated/user-controller/user-controller';

export const Registration = () => {
  const auth = useKeycloakAuth();
  const { t } = useTranslation();
  const [searchParams] = useSearchParams();
  const inviteToken = searchParams.get('invitetoken');
  const registerMutation = useRegister();
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState('');

  const validateForm = () => {
    const newErrors: Record<string, string> = {};

    if (!formData.firstName.trim()) {
      newErrors.firstName = t('registration.first-name-required');
    }
    if (!formData.lastName.trim()) {
      newErrors.lastName = t('registration.last-name-required');
    }
    if (!formData.email.trim()) {
      newErrors.email = t('registration.email-required');
    } else if (!/\S+@\S+\.\S+/.test(formData.email)) {
      newErrors.email = t('registration.email-invalid');
    }
    if (!formData.password) {
      newErrors.password = t('registration.password-required');
    } else if (formData.password.length < 6) {
      newErrors.password = t('registration.password-length');
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: value,
    }));

    if (errors[name]) {
      setErrors((prev) => ({
        ...prev,
        [name]: '',
      }));
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitError('');

    if (!validateForm()) {
      return;
    }

    if (!inviteToken) {
      setSubmitError(t('registration.invalid-link'));
      return;
    }

    localStorage.removeItem('token');

    registerMutation.mutate(
      {
        data: {
          firstName: formData.firstName,
          lastName: formData.lastName,
          email: formData.email,
          password: formData.password,
          inviteToken,
        },
      },
      {
        onSuccess: () => {
          auth.signinRedirect();
        },
        onError: (error: unknown) => {
          setSubmitError(t('registration.failed'));
          console.error('Registration error:', error);
        },
      }
    );
  };

  return (
    <Container maxWidth="sm" sx={{ display: 'flex', alignItems: 'center', minHeight: '100vh' }}>
      <Paper elevation={3} sx={{ p: 4, width: '100%', borderRadius: 2 }}>
        <Typography variant="h4" gutterBottom sx={{ fontWeight: 'bold', textAlign: 'center', mb: 3 }}>
          {t('registration.title')}
        </Typography>

        {submitError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {submitError}
          </Alert>
        )}

        <Box component="form" onSubmit={handleSubmit} noValidate>
          <TextField
            fullWidth
            label={t('profile.first-name')}
            name="firstName"
            value={formData.firstName}
            onChange={handleChange}
            error={!!errors.firstName}
            helperText={errors.firstName}
            margin="normal"
            variant="outlined"
          />

          <TextField
            fullWidth
            label={t('profile.last-name')}
            name="lastName"
            value={formData.lastName}
            onChange={handleChange}
            error={!!errors.lastName}
            helperText={errors.lastName}
            margin="normal"
            variant="outlined"
          />

          <TextField
            fullWidth
            label={t('admin.email')}
            name="email"
            type="email"
            value={formData.email}
            onChange={handleChange}
            error={!!errors.email}
            helperText={errors.email}
            margin="normal"
            variant="outlined"
          />

          <TextField
            fullWidth
            label={t('admin.password')}
            name="password"
            type="password"
            value={formData.password}
            onChange={handleChange}
            error={!!errors.password}
            helperText={errors.password}
            margin="normal"
            variant="outlined"
          />

          <Button
            fullWidth
            variant="contained"
            color="primary"
            size="large"
            type="submit"
            disabled={registerMutation.isPending}
            sx={{ mt: 3, py: 1.5 }}
          >
            {registerMutation.isPending ? t('registration.creating') : t('registration.submit')}
          </Button>
        </Box>

        <Typography variant="body2" sx={{ textAlign: 'center', mt: 2 }}>
          {t('registration.already-have-account')}{' '}
          <Button
            variant="text"
            size="small"
            onClick={() => {
              if (inviteToken) localStorage.setItem('pendingInviteToken', inviteToken);
              auth.signinRedirect();
            }}
            sx={{ textTransform: 'none', p: 0, ml: 0.5 }}
          >
            {t('registration.login-here')}
          </Button>
        </Typography>
      </Paper>
    </Container>
  );
};
