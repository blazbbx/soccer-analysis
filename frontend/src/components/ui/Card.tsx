import { Box, useTheme, type BoxProps } from '@mui/material';

interface CardProps extends BoxProps {
  interactive?: boolean;
}

export const Card = ({ interactive, sx, children, ...rest }: CardProps) => {
  const theme = useTheme();

  return (
    <Box
      sx={{
        bgcolor: 'background.paper',
        border: '1px solid',
        borderColor: 'divider',
        borderRadius: `${theme.shape.borderRadius}px`,
        p: 2,
        ...(interactive && {
          transition: 'border-color 0.2s ease',
          '&:hover': { borderColor: 'primary.main' },
        }),
        ...sx,
      }}
      {...rest}
    >
      {children}
    </Box>
  );
};
