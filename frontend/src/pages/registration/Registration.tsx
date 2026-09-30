import { useEffect, useState } from 'react';
import {
  Container,
  Paper,
  TextField,
  Button,
  Typography,
  Box,
  Alert,
} from '@mui/material';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useAuth as useKeycloakAuth } from 'react-oidc-context';
import { useAuth } from '../../context/AuthContext';
import { ROLES } from '../../types/roles';
import { useTranslation } from 'react-i18next';
import type { AxiosError } from 'axios';
import { useRegister } from '../../api/generated/user-controller/user-controller';
import { acceptInviteWithDetails, getInviteDetails } from '../../api/children';
import { MY_CHILDREN_QUERY_KEY } from '../../context/ChildrenContext';
import { formatPersonName } from '../../utils/stringUtils';

export const Registration = () => {
  const auth = useKeycloakAuth();
  const { user } = useAuth();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const inviteToken = searchParams.get('invitetoken');
  const queryClient = useQueryClient();
  const registerMutation = useRegister();
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState('');
  const [childFirstName, setChildFirstName] = useState<string | null>(null);
  const [childLastName, setChildLastName] = useState<string | null>(null);
  const [inviteKind, setInviteKind] = useState<string | null>(null);
  const [playerDateOfBirth, setPlayerDateOfBirth] = useState('');

  useEffect(() => {
    if (!inviteToken) return;
    getInviteDetails(inviteToken)
      .then((invite) => {
        setInviteKind(invite.inviteKind ?? null);
        if (invite.childFirstName) {
          setChildFirstName(invite.childFirstName);
        }
        if (invite.childLastName) {
          setChildLastName(invite.childLastName);
        }
      })
      .catch(() => {
        setChildFirstName(null);
        setChildLastName(null);
        setInviteKind(null);
      });
  }, [inviteToken]);

  useEffect(() => {
    if (!auth.isAuthenticated || auth.isLoading || !inviteToken) return;
    let cancelled = false;
    getInviteDetails(inviteToken)
      .then((invite) => {
        if (cancelled) return;
        const needsDetails =
          (invite.inviteKind === 'PARENT' && !invite.childFirstName) ||
          invite.inviteKind === 'PLAYER_SELF';
        if (invite.inviteKind === 'CLUB_ADMIN' && user?.role !== ROLES.CLUB_ADMIN) {
          return;
        }
        if (needsDetails) {
          navigate(`/invite-accept?token=${encodeURIComponent(inviteToken)}`, { replace: true });
          return;
        }
        return acceptInviteWithDetails(inviteToken, {}).then(() => {
          if (cancelled) return;
          queryClient.invalidateQueries({ queryKey: MY_CHILDREN_QUERY_KEY });
          navigate('/', { replace: true });
        });
      })
      .catch(() => {
        if (!cancelled) setSubmitError(t('registration.invalid-link'));
      });
    return () => {
      cancelled = true;
    };
  }, [auth.isAuthenticated, auth.isLoading, inviteToken, navigate, queryClient, t, user?.role]);

  const openParentInvite = inviteKind === 'PARENT' && !childFirstName;
  const playerSelfInvite = inviteKind === 'PLAYER_SELF';

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
          dateOfBirth: playerSelfInvite ? playerDateOfBirth || undefined : undefined,
        },
      },
      {
        onSuccess: () => {
          if (inviteToken && openParentInvite) {
            localStorage.setItem('pendingInviteToken', inviteToken);
          }
          auth.signinRedirect();
        },
        onError: (error: unknown) => {
          const response = (error as AxiosError<{ detail?: string; errors?: { email?: string[] } }> | undefined)
            ?.response;
          const status = response?.status;
          const emailTaken = Boolean(response?.data?.errors?.email);
          if (status === 409 && inviteToken && emailTaken) {
            localStorage.setItem('pendingInviteToken', inviteToken);
            setSubmitError(t('registration.email-exists-login'));
          } else {
            setSubmitError(response?.data?.detail || t('registration.failed'));
          }
          console.error('Registration error:', error);
        },
      }
    );
  };

  const registerWhileLoggedIn = inviteKind === 'CLUB_ADMIN' && user?.role !== ROLES.CLUB_ADMIN;
  if (auth.isAuthenticated && inviteToken && !registerWhileLoggedIn) {
    return (
      <Container maxWidth="sm" sx={{ display: 'flex', alignItems: 'center', minHeight: '100vh' }}>
        <Paper elevation={3} sx={{ p: 4, width: '100%', borderRadius: 2, textAlign: 'center' }}>
          {submitError ? (
            <Alert severity="error">{submitError}</Alert>
          ) : (
            <Typography color="text.secondary">{t('registration.opening-invite')}</Typography>
          )}
        </Paper>
      </Container>
    );
  }

  return (
    <Container maxWidth="sm" sx={{ display: 'flex', alignItems: 'center', minHeight: '100vh' }}>
      <Paper elevation={3} sx={{ p: 4, width: '100%', borderRadius: 2 }}>
        <Typography variant="h4" gutterBottom sx={{ fontWeight: 'bold', textAlign: 'center', mb: 3 }}>
          {inviteKind === 'CLUB_ADMIN'
            ? t('registration.club-admin-title')
            : childFirstName
            ? t(inviteKind === 'CHILD_LOGIN' ? 'registration.child-login-title' : 'registration.parent-title', { name: formatPersonName(childFirstName, childLastName) })
            : openParentInvite
              ? t('registration.open-parent-title')
              : t('registration.title')}
        </Typography>

        {submitError && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {submitError}
          </Alert>
        )}

        <Box component="form" onSubmit={handleSubmit} noValidate>
          {openParentInvite && (
            <Alert severity="info" sx={{ mb: 2 }}>
              {t('registration.child-later')}
            </Alert>
          )}
          {playerSelfInvite && (
            <TextField
              fullWidth
              label={t('registration.player-dob')}
              type="date"
              value={playerDateOfBirth}
              onChange={(event) => setPlayerDateOfBirth(event.target.value)}
              margin="normal"
              helperText={t('registration.player-dob-help')}
              InputLabelProps={{ shrink: true }}
            />
          )}
          <TextField
            fullWidth
            label={openParentInvite ? t('registration.parent-first-name') : t('profile.first-name')}
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
            label={openParentInvite ? t('registration.parent-last-name') : t('profile.last-name')}
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
            label={openParentInvite ? t('registration.parent-email') : t('admin.email')}
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
            label={openParentInvite ? t('registration.parent-password') : t('admin.password')}
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
