import { Button as MuiButton, type ButtonProps } from '@mui/material';

export const PrimaryButton = (props: ButtonProps) => (
  <MuiButton variant="outlined" color="primary" {...props} sx={{ py: 1.5, fontWeight: 'bold', ...props.sx }} />
);

export const SecondaryButton = (props: ButtonProps) => (
  <MuiButton variant="text" color="primary" size="small" {...props} sx={{ fontWeight: 500, ...props.sx }} />
);

export const FilledActionButton = (props: ButtonProps) => (
  <MuiButton variant="contained" color="primary" {...props} sx={{ py: 1.5, px: 3, fontWeight: 'bold', ...props.sx }} />
);

export const DeleteButton = (props: ButtonProps) => (
  <MuiButton variant="contained" color="error" {...props} />
);
