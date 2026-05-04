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
import type { MatchResponse, UpdateMatchRequest } from '../../../api/generated/model';

interface EditMatchDialogProps {
  open: boolean;
  match: MatchResponse | null;
  onClose: () => void;
  onSave: (id: string, data: UpdateMatchRequest) => void;
}

export const EditMatchDialog = ({ open, match, onClose, onSave }: EditMatchDialogProps) => {
  const { t } = useTranslation();
  const [form, setForm] = useState<UpdateMatchRequest>({
    homeScore: 0,
    awayScore: 0,
    matchDate: '',
  });

  useEffect(() => {
    if (match) {
      setForm({
        homeTeamId: match.homeTeamId ?? '',
        awayTeamId: match.awayTeamId ?? '',
        homeScore: match.homeScore ?? 0,
        awayScore: match.awayScore ?? 0,
        matchDate: match.matchDate ?? '',
      });
    }
  }, [match]);

  const handleNumberChange = (field: 'homeScore' | 'awayScore') => (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((prev) => ({ ...prev, [field]: Number(e.target.value) }));
  };

  const handleStringChange = (field: 'homeTeamId' | 'awayTeamId' | 'matchDate') => (e: React.ChangeEvent<HTMLInputElement>) => {
    setForm((prev) => ({ ...prev, [field]: e.target.value }));
  };

  const handleSubmit = () => {
    if (!match?.id) return;
    onSave(match.id, form);
    onClose();
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontWeight: 'bold' }}>{t('admin.edit-match')}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={3} sx={{ mt: 1 }}>
          <TextField
            label={t('admin.home-team-id')}
            value={form.homeTeamId ?? ''}
            onChange={handleStringChange('homeTeamId')}
            fullWidth
          />
          <TextField
            label={t('admin.away-team-id')}
            value={form.awayTeamId ?? ''}
            onChange={handleStringChange('awayTeamId')}
            fullWidth
          />
          <TextField
            label={t('admin.home-score')}
            value={form.homeScore ?? 0}
            onChange={handleNumberChange('homeScore')}
            fullWidth
            type="number"
            inputProps={{ min: 0 }}
          />
          <TextField
            label={t('admin.away-score')}
            value={form.awayScore ?? 0}
            onChange={handleNumberChange('awayScore')}
            fullWidth
            type="number"
            inputProps={{ min: 0 }}
          />
          <TextField
            label={t('admin.match-date')}
            value={form.matchDate ?? ''}
            onChange={handleStringChange('matchDate')}
            fullWidth
            type="datetime-local"
            slotProps={{ inputLabel: { shrink: true } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        <SecondaryButton onClick={onClose}>{t('common.cancel')}</SecondaryButton>
        <PrimaryButton onClick={handleSubmit}>{t('common.save')}</PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
