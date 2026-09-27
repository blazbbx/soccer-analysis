import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { Box, CircularProgress } from '@mui/material';
import { type Role } from '../../types/roles'; 

interface RoleRouteProps {
  allowedRoles: Role[]; 
}

export const RoleRoute = ({ allowedRoles }: RoleRouteProps) => {
  const { user, isLoading } = useAuth();

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

  const userRoles = user.roles?.length ? user.roles : [user.role];
  if (!allowedRoles.some((role) => userRoles.includes(role))) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
};