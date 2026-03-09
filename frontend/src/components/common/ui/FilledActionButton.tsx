import { Button, ButtonProps } from '@mui/material';
import AddIcon from '@mui/icons-material/Add'; 
import { APP_COLORS } from '../../../constants/colors'; 

export const FilledActionButton = (props: ButtonProps) => {
  return (
    <Button
      variant="contained" 
      startIcon={<AddIcon />} 
      {...props} 
      sx={{
        py: 1.5,
        px: 3, 
        borderRadius: 2,
        fontWeight: 'bold',
        textTransform: 'none',
        backgroundColor: APP_COLORS.filledAction.main,
        color: APP_COLORS.filledAction.contrastText,
        '& .MuiButton-startIcon': {
          color: 'inherit'
        },
        '&:hover': {
          backgroundColor: APP_COLORS.filledAction.hover,
        },
        ...props.sx, 
      }}
    />
  );
};