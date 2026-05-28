import { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Stack,
  MenuItem,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { PrimaryButton } from '../../../components/ui/PrimaryButton';
import { SecondaryButton } from '../../../components/ui/SecondaryButton';
import type { CreateUserRequest } from '../../../api/generated/model';

const ROLES = ['ADMIN', 'COACH', 'PLAYER', 'FAN'] as const;

interface CreateUserDialogProps {
  open: boolean;
  onClose: () => void;
  onCreate: (data: CreateUserRequest) => void;
}

export const CreateUserDialog = ({ open, onClose, onCreate }: CreateUserDialogProps) => {
  const { t } = useTranslation();
  const [form, setForm] = useState<CreateUserRequest>({
    email: '',
    firstName: '',
    lastName: '',
    password: '',
    role: 'PLAYER',
  });

  const handleChange = (field: keyof CreateUserRequest) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }));
  };

  const handleSubmit = () => {
    onCreate(form);
    setForm({ email: '', firstName: '', lastName: '', password: '', role: 'PLAYER' });
    onClose();
  };

  const isValid =
    form.email.trim() !== '' &&
    form.firstName.trim().length >= 2 &&
    form.lastName.trim().length >= 2 &&
    form.password.length >= 6;

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontWeight: 'bold' }}>{t('admin.create-user')}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={3} sx={{ mt: 1 }}>
          <TextField
            label={t('admin.email')}
            value={form.email}
            onChange={handleChange('email')}
            fullWidth
            type="email"
          />
          <TextField
            label={t('profile.first-name')}
            value={form.firstName}
            onChange={handleChange('firstName')}
            fullWidth
          />
          <TextField
            label={t('profile.last-name')}
            value={form.lastName}
            onChange={handleChange('lastName')}
            fullWidth
          />
          <TextField
            label={t('admin.password')}
            value={form.password}
            onChange={handleChange('password')}
            fullWidth
            type="password"
          />
          <TextField
            label={t('admin.select-role')}
            value={form.role}
            onChange={handleChange('role')}
            select
            fullWidth
          >
            {ROLES.map((r) => (
              <MenuItem key={r} value={r}>
                {t(`roles.${r.toLowerCase()}`)}
              </MenuItem>
            ))}
          </TextField>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        <SecondaryButton onClick={onClose}>{t('common.cancel')}</SecondaryButton>
        <PrimaryButton onClick={handleSubmit} disabled={!isValid}>
          {t('common.create')}
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
