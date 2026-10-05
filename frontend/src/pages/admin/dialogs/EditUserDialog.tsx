import { useState, useEffect } from 'react';
import { TextField, Stack } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { FormDialog } from '../../../components/ui/FormDialog';
import type { UpdateUserRequest, UserResponse } from '../../../api/generated/model';

interface EditUserDialogProps {
  open: boolean;
  user: UserResponse | null;
  onClose: () => void;
  onSave: (id: string, data: UpdateUserRequest) => void;
}

export const EditUserDialog = ({ open, user, onClose, onSave }: EditUserDialogProps) => {
  const { t } = useTranslation();
  const [form, setForm] = useState<UpdateUserRequest>({ firstName: '', lastName: '' });

  useEffect(() => {
    if (user) {
      setForm({ firstName: user.firstName ?? '', lastName: user.lastName ?? '' });
    }
  }, [user]);

  const handleChange = (field: keyof UpdateUserRequest) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }));
  };

  const handleSubmit = () => {
    if (!user?.id) return;
    onSave(user.id, form);
    onClose();
  };

  return (
    <FormDialog open={open} title={t('admin.edit-user')} onClose={onClose} onSubmit={handleSubmit}>
      <Stack spacing={3} sx={{ mt: 1 }}>
        <TextField
          label={t('profile.first-name')}
          value={form.firstName ?? ''}
          onChange={handleChange('firstName')}
          fullWidth
        />
        <TextField
          label={t('profile.last-name')}
          value={form.lastName ?? ''}
          onChange={handleChange('lastName')}
          fullWidth
        />
      </Stack>
    </FormDialog>
  );
};
