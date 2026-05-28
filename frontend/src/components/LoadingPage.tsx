import { Box, CircularProgress } from '@mui/material';

export const LoadingPage = () => (
  <Box
    sx={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      minHeight: '40vh',
    }}
  >
    <CircularProgress />
  </Box>
);
