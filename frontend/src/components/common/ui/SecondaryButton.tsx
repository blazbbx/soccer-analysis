import { Button, type ButtonProps } from '@mui/material';

export const SecondaryButton = (props: ButtonProps) => {
  return (
    <Button
      variant="text" 
      size="small" 
      {...props}
      sx={{
        color: '#10b981', 
        textTransform: 'none',
        fontWeight: 500,
        borderRadius: 2,
        '&:hover': {
          backgroundColor: 'rgba(16, 185, 129, 0.1)', 
        },
        ...props.sx,
      }}
    />
  );
};