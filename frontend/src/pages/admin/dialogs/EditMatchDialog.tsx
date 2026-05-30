import { useState, useEffect } from 'react';
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
import type { MatchResponse, UpdateMatchRequest, TeamResponse } from '../../../api/generated/model';

interface EditMatchDialogProps {
  open: boolean;
  match: MatchResponse | null;
  teams: TeamResponse[];
  onClose: () => void;
  onSave: (id: string, data: UpdateMatchRequest) => void;
}

export const EditMatchDialog = ({ open, match, teams, onClose, onSave }: EditMatchDialogProps) => {
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
            select
            label={t('admin.home-team')}
            value={form.homeTeamId ?? ''}
            onChange={(e) => setForm((prev) => ({ ...prev, homeTeamId: e.target.value }))}
            fullWidth
          >
            {teams.map((team) => (
              <MenuItem key={team.id} value={team.id ?? ''}>
                {team.name}
              </MenuItem>
            ))}
          </TextField>

          <TextField
            select
            label={t('admin.away-team')}
            value={form.awayTeamId ?? ''}
            onChange={(e) => setForm((prev) => ({ ...prev, awayTeamId: e.target.value }))}
            fullWidth
          >
            {teams.map((team) => (
              <MenuItem key={team.id} value={team.id ?? ''}>
                {team.name}
              </MenuItem>
            ))}
          </TextField>

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
            onChange={(e) => setForm((prev) => ({ ...prev, matchDate: e.target.value }))}
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
