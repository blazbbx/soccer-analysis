import { ConfirmDialog } from '../../../components/ui/ConfirmDialog';

interface ConfirmDeleteDialogProps {
  open: boolean;
  title: string;
  description: string;
  onConfirm: () => void;
  onClose: () => void;
}

export const ConfirmDeleteDialog = (props: ConfirmDeleteDialogProps) => (
  <ConfirmDialog tone="danger" {...props} />
);
