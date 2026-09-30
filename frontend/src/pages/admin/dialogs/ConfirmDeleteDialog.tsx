import { HoldToDeleteDialog } from '../../clubs/dialogs/HoldToDeleteDialog';

interface ConfirmDeleteDialogProps {
  open: boolean;
  title: string;
  description: string;
  onConfirm: () => void;
  onClose: () => void;
}

export const ConfirmDeleteDialog = ({
  open,
  title,
  description,
  onConfirm,
  onClose,
}: ConfirmDeleteDialogProps) => (
  <HoldToDeleteDialog
    open={open}
    title={title}
    itemName=""
    description={description}
    onClose={onClose}
    onConfirm={() => {
      onConfirm();
      onClose();
    }}
  />
);
