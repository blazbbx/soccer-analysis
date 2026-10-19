import { HoldToDeleteDialog } from '../../clubs/dialogs/HoldToDeleteDialog';

interface ConfirmDeleteDialogProps {
  open: boolean;
  title: string;
  description: string;
  warningText?: string;
  hold?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

export const ConfirmDeleteDialog = ({
  open,
  title,
  description,
  warningText,
  hold = false,
  onConfirm,
  onClose,
}: ConfirmDeleteDialogProps) => (
  <HoldToDeleteDialog
    open={open}
    title={title}
    itemName=""
    description={description}
    warningText={warningText}
    hold={hold}
    onClose={onClose}
    onConfirm={() => {
      onConfirm();
      onClose();
    }}
  />
);