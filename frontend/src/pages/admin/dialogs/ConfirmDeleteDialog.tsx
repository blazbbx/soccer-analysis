import { HoldToDeleteDialog } from '../../clubs/dialogs/HoldToDeleteDialog';

interface ConfirmDeleteDialogProps {
  open: boolean;
  title: string;
  description: string;
  warningText?: string;
  onConfirm: () => void;
  onClose: () => void;
}

export const ConfirmDeleteDialog = ({
  open,
  title,
  description,
  warningText,
  onConfirm,
  onClose,
}: ConfirmDeleteDialogProps) => (
  <HoldToDeleteDialog
    open={open}
    title={title}
    itemName=""
    description={description}
    warningText={warningText}
    onClose={onClose}
    onConfirm={() => {
      onConfirm();
      onClose();
    }}
  />
);