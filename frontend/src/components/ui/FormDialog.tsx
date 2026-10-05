import { Alert, Dialog, DialogTitle, DialogContent, DialogActions } from '@mui/material';
import { useTranslation } from 'react-i18next';
import { PrimaryButton, SecondaryButton } from './Button';

interface FormDialogProps {
  open: boolean;
  title: string;
  onClose: () => void;
  onSubmit: () => void;
  submitLabel?: string;
  cancelLabel?: string;
  maxWidth?: 'xs' | 'sm' | 'md' | 'lg';
  error?: string | null;
  children: React.ReactNode;
}

export const FormDialog = ({
  open,
  title,
  onClose,
  onSubmit,
  submitLabel,
  cancelLabel,
  maxWidth = 'sm',
  error,
  children,
}: FormDialogProps) => {
  const { t } = useTranslation();

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth={maxWidth}>
      <DialogTitle sx={{ fontWeight: 'bold' }}>{title}</DialogTitle>
      <DialogContent dividers>
        {children}
        {error && (
          <Alert severity="error" sx={{ mt: 3 }}>
            {error}
          </Alert>
        )}
      </DialogContent>
      <DialogActions sx={{ p: 2 }}>
        <SecondaryButton onClick={onClose}>{cancelLabel ?? t('common.cancel')}</SecondaryButton>
        <PrimaryButton variant="contained" onClick={onSubmit}>
          {submitLabel ?? t('common.save')}
        </PrimaryButton>
      </DialogActions>
    </Dialog>
  );
};
