import { useState, useEffect } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  Stack,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { PrimaryButton } from '../../../components/ui/PrimaryButton';
import { SecondaryButton } from '../../../components/ui/SecondaryButton';
import type { TeamResponse, UpdateTeamRequest } from '../../../api/generated/model';

interface EditTeamDialogProps {
  open: boolean;
  team: TeamResponse | null;
  onClose: () => void;
  onSave: (id: string, data: UpdateTeamRequest) => void;
}

export const EditTeamDialog = ({ open, team, onClose, onSave }: EditTeamDialogProps) => {
  const { t } = useTranslation();
  const [form, setForm] = useState<UpdateTeamRequest>({ name: '', shortName: '' });

  useEffect(() => {
    if (team) {
      setForm({ name: team.name ?? '', shortName: team.shortName ?? '' });
    }
  }, [team]);

  const handleChange = (field: keyof UpdateTeamRequest) => (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }));
  };

  const handleSubmit = () => {
    if (!team?.id) return;
    onSave(team.id, form);
    onClose();
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontWeight: 'bold' }}>{t('admin.edit-team')}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={3} sx={{ mt: 1 }}>
          <TextField
            label={t('teams.name-label')}
            value={form.name}
            onChange={handleChange('name')}
            fullWidth
            error={form.name.trim() === ''}
            helperText={form.name.trim() === '' ? t('teams.name-error') : ''}
          />
          <TextField
            label="Short Name"
            value={form.shortName ?? ''}
            onChange={handleChange('shortName')}
            fullWidth
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        <SecondaryButton onClick={onClose}>{t('common.cancel')}</SecondaryButton>
        <PrimaryButton onClick={handleSubmit} disabled={form.name.trim() === ''}>
          {t('common.save')}
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
