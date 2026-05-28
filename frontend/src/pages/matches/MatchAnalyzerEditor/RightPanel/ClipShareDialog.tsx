import { useState } from 'react';
import {
  Box,
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import CheckIcon from '@mui/icons-material/Check';
import { useTranslation } from 'react-i18next';

interface ClipShareDialogProps {
  open: boolean;
  onClose: () => void;
  clipId: string;
}

export const ClipShareDialog = ({ open, onClose, clipId }: ClipShareDialogProps) => {
  const { t } = useTranslation();
  const [copied, setCopied] = useState(false);

  const shareUrl = `${window.location.origin}/clip/${clipId}`;

  const handleCopy = () => {
    navigator.clipboard.writeText(shareUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle
        sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', py: 1.5 }}
      >
        <Typography sx={{ fontWeight: 600, fontSize: '15px' }}>
          {t('clips.share.dialog-title')}
        </Typography>
        <IconButton size="small" onClick={onClose}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 0.5 }}>
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            {t('clips.share.description')}
          </Typography>
          <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
            <TextField
              value={shareUrl}
              InputProps={{ readOnly: true }}
              fullWidth
              size="small"
              sx={{ '& .MuiInputBase-input': { fontSize: '13px' } }}
            />
            <IconButton onClick={handleCopy} size="small" color={copied ? 'success' : 'default'}>
              {copied
                ? <CheckIcon fontSize="small" />
                : <ContentCopyIcon fontSize="small" />
              }
            </IconButton>
          </Box>
        </Stack>
      </DialogContent>
    </Dialog>
  );
};
