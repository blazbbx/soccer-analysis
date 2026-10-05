import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
} from '@mui/material';
import { useTranslation } from 'react-i18next';
import { PrimaryButton, SecondaryButton, DeleteButton } from './Button';

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  description: React.ReactNode;
  confirmLabel?: string;
  cancelLabel?: string;
  tone?: 'default' | 'danger';
  onConfirm: () => void;
  onClose: () => void;
}

export const ConfirmDialog = ({
  open,
  title,
  description,
  confirmLabel,
  cancelLabel,
  tone = 'danger',
  onConfirm,
  onClose,
}: ConfirmDialogProps) => {
  const { t } = useTranslation();

  const handleConfirm = () => {
    onConfirm();
    onClose();
  };

  const ConfirmButton = tone === 'danger' ? DeleteButton : PrimaryButton;
  const defaultConfirmLabel = tone === 'danger' ? t('common.delete') : t('common.confirm');

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ fontWeight: 'bold' }}>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText>{description}</DialogContentText>
      </DialogContent>
      <DialogActions sx={{ p: 2, gap: 1 }}>
        <SecondaryButton onClick={onClose}>{cancelLabel ?? t('common.cancel')}</SecondaryButton>
        <ConfirmButton onClick={handleConfirm}>{confirmLabel ?? defaultConfirmLabel}</ConfirmButton>
      </DialogActions>
    </Dialog>
  );
};
