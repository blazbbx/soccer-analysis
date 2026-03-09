import { Button, ButtonProps } from '@mui/material';

export const PrimaryButton = (props: ButtonProps) => {
  return (
    <Button
      variant="outlined" 
      {...props} 
      sx={{
        py: 1.5, 
        borderRadius: 2,
        fontWeight: 'bold',
        textTransform: 'none',
        color: '#10b981', 
        borderColor: 'rgba(16, 185, 129, 0.3)', 
        backgroundColor: 'rgba(16, 185, 129, 0.05)', 
        '&:hover': {
          backgroundColor: 'rgba(16, 185, 129, 0.1)',
          borderColor: '#10b981',
        },
        ...props.sx, 
      }}
    />
  );
};