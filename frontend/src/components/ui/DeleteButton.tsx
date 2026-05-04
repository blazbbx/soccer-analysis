import { Button, type ButtonProps } from '@mui/material';

export const DangerButton = (props: ButtonProps) => {
  return (
    <Button 
      color="error" 
      variant="contained" 
      
      {...props}
    >
      {props.children}
    </Button>
  );
};