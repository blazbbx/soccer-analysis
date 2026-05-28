import { useState } from 'react';
import {
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { PrimaryButton } from '../../../components/ui/PrimaryButton';
import { SecondaryButton } from '../../../components/ui/SecondaryButton';

interface SaveClipDialogProps {
  open: boolean;
  loading: boolean;
  onSave: (name: string) => void;
  onClose: () => void;
}

export const SaveClipDialog = ({ open, loading, onSave, onClose }: SaveClipDialogProps) => {
  const { t } = useTranslation();
  const [name, setName] = useState('');
  const [error, setError] = useState('');

  const handleSave = () => {
    const trimmed = name.trim();
    if (!trimmed) {
      setError(t('editor.clip-name-required'));
      return;
    }
    onSave(trimmed);
  };

  const handleClose = () => {
    if (loading) return;
    setName('');
    setError('');
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="xs" fullWidth>
      <DialogTitle>{t('editor.save-clip-title')}</DialogTitle>
      <DialogContent>
        <TextField
          autoFocus
          fullWidth
          label={t('editor.clip-name')}
          value={name}
          onChange={(e) => {
            if (e.target.value.length <= 255) setName(e.target.value);
            if (error) setError('');
          }}
          error={!!error}
          helperText={error}
          disabled={loading}
          sx={{ mt: 1 }}
        />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <SecondaryButton onClick={handleClose} disabled={loading}>
          {t('common.cancel')}
        </SecondaryButton>
        <PrimaryButton onClick={handleSave} disabled={loading || !name.trim()}>
          {loading ? <CircularProgress size={18} color="inherit" /> : t('common.save')}
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
