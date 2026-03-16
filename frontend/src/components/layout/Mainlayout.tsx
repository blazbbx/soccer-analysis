import { Box } from '@mui/material';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';

export const MainLayout = () => {
  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      {/* Bal oldali menü */}
      <Sidebar />
      
      {/* Jobb oldali dinamikus tartalom */}
      <Box component="main" sx={{ flexGrow: 1, p: 4, overflow: 'auto' }}>
        <Outlet />
      </Box>
    </Box>
  );
};