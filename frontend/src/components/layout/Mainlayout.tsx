import { Box } from '@mui/material';
import { Outlet } from 'react-router-dom';
import { ChildrenProvider } from '../../context/ChildrenContext';
import { Sidebar } from './Sidebar';

export const MainLayout = () => {
  return (
    <ChildrenProvider>
      <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
        <Sidebar />
        <Box component="main" sx={{ flexGrow: 1, p: 0, overflow: 'auto' }}>
          <Outlet />
        </Box>
      </Box>
    </ChildrenProvider>
  );
};