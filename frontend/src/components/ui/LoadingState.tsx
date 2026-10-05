import { Box, CircularProgress, Typography, type SxProps, type Theme } from '@mui/material';

interface LoadingStateProps {
  size?: number;
  minHeight?: number | string;
  label?: string;
  sx?: SxProps<Theme>;
}

export const LoadingState = ({ size = 40, minHeight = '40vh', label, sx }: LoadingStateProps) => (
  <Box
    sx={{
      display: 'flex',
      flexDirection: 'column',
      gap: 1.5,
      justifyContent: 'center',
      alignItems: 'center',
      minHeight,
      width: '100%',
      ...sx,
    }}
  >
    <CircularProgress size={size} />
    {label && (
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
    )}
  </Box>
);
