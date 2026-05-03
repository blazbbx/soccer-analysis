import { useState } from 'react';
import {
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  TextField,
} from '@mui/material';
import { PrimaryButton } from '../../../components/ui/PrimaryButton';
import { SecondaryButton } from '../../../components/ui/SecondaryButton';

interface SaveClipDialogProps {
  open: boolean;
  loading: boolean;
  onSave: (name: string) => void;
  onClose: () => void;
}

export const SaveClipDialog = ({ open, loading, onSave, onClose }: SaveClipDialogProps) => {
  const [name, setName] = useState('');
  const [error, setError] = useState('');

  const handleSave = () => {
    const trimmed = name.trim();
    if (!trimmed) {
      setError('Clip name is required');
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
      <DialogTitle>Save clip</DialogTitle>
      <DialogContent>
        <TextField
          autoFocus
          fullWidth
          label="Clip name"
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
          Cancel
        </SecondaryButton>
        <PrimaryButton onClick={handleSave} disabled={loading || !name.trim()}>
          {loading ? <CircularProgress size={18} color="inherit" /> : 'Save'}
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
