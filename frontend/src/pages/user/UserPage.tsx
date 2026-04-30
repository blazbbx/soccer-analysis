import { useState } from 'react';
import {
  Container,
  Stack,
  Typography,
  Box,
  Paper,
  Avatar,
  Chip,
  TextField,
  Button,
  CircularProgress,
  Alert,
  Snackbar,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../../context/AuthContext';
import { updateUserProfile } from '../../services/userService';

interface ProfileFormState {
  firstName: string;
  lastName: string;
}

type SaveStatus = 'idle' | 'success' | 'error';

const splitName = (fullName: string): ProfileFormState => {
  const spaceIndex = fullName.indexOf(' ');
  if (spaceIndex === -1) return { firstName: fullName, lastName: '' };
  return {
    firstName: fullName.slice(0, spaceIndex),
    lastName: fullName.slice(spaceIndex + 1),
  };
};

export const UserPage = () => {
  const { t } = useTranslation();
  const { user } = useAuth();

  const [formState, setFormState] = useState<ProfileFormState>(
    () => splitName(user?.name ?? ''),
  );
  const [isSaving, setIsSaving] = useState(false);
  const [saveStatus, setSaveStatus] = useState<SaveStatus>('idle');

  const handleSave = async () => {
    setIsSaving(true);
    try {
      await updateUserProfile({
        firstName: formState.firstName,
        lastName: formState.lastName,
      });
      setSaveStatus('success');
    } catch {
      setSaveStatus('error');
    } finally {
      setIsSaving(false);
    }
  };

  const avatarInitials =
    `${formState.firstName[0] ?? ''}${formState.lastName[0] ?? ''}`.toUpperCase();

  return (
    <Container maxWidth="sm" sx={{ py: 6 }}>
      <Stack spacing={4}>
        <Typography variant="h4" fontWeight="bold">
          {t('profile.title')}
        </Typography>

        <Paper sx={{ p: 4, borderRadius: 3 }}>
          <Stack spacing={3}>
            <Box sx={{ display: 'flex', justifyContent: 'center' }}>
              <Avatar
                sx={{
                  width: 80,
                  height: 80,
                  bgcolor: '#0f766e',
                  fontSize: '2rem',
                }}
              >
                {avatarInitials}
              </Avatar>
            </Box>

            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Typography variant="body2" color="text.secondary">
                {t('profile.role')}:
              </Typography>
              <Chip
                label={user?.role ? t(`roles.${user.role}`) : '—'}
                size="small"
                sx={{ bgcolor: 'rgba(16,185,129,0.1)', color: '#10b981' }}
              />
            </Box>

            <TextField
              label={t('profile.first-name')}
              value={formState.firstName}
              onChange={(e) =>
                setFormState((prev) => ({ ...prev, firstName: e.target.value }))
              }
              fullWidth
            />

            <TextField
              label={t('profile.last-name')}
              value={formState.lastName}
              onChange={(e) =>
                setFormState((prev) => ({ ...prev, lastName: e.target.value }))
              }
              fullWidth
            />

            <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
              <Button
                variant="contained"
                onClick={handleSave}
                disabled={isSaving}
                startIcon={
                  isSaving ? (
                    <CircularProgress size={16} color="inherit" />
                  ) : undefined
                }
              >
                {t('profile.save')}
              </Button>
            </Box>
          </Stack>
        </Paper>
      </Stack>

      <Snackbar
        open={saveStatus !== 'idle'}
        autoHideDuration={4000}
        onClose={() => setSaveStatus('idle')}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          severity={saveStatus === 'success' ? 'success' : 'error'}
          onClose={() => setSaveStatus('idle')}
        >
          {saveStatus === 'success'
            ? t('profile.save-success')
            : t('profile.save-error')}
        </Alert>
      </Snackbar>
    </Container>
  );
};
