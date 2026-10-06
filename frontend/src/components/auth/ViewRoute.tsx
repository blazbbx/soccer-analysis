import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useView } from '../../context/ViewContext';
import { Box, CircularProgress } from '@mui/material';
import { type Role } from '../../types/roles';

interface ViewRouteProps {
  allowedViews: Role[];
}

export const ViewRoute = ({ allowedViews }: ViewRouteProps) => {
  const { user, isLoading } = useAuth();
  const { activeView } = useView();

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (!allowedViews.includes(activeView)) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
};
