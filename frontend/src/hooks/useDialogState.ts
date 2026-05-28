import { useState } from 'react';

interface DialogState<T> {
  open: boolean;
  data: T | null;
}

export const useDialogState = <T,>() => {
  const [state, setState] = useState<DialogState<T>>({ open: false, data: null });

  const openWith = (data: T) => setState({ open: true, data });
  const close = () => setState({ open: false, data: null });

  return { open: state.open, data: state.data, openWith, close };
};
