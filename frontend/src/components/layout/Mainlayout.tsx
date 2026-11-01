import { Box } from '@mui/material';
import { Outlet } from 'react-router-dom';
import { ChildrenProvider } from '../../context/ChildrenContext';
import { Sidebar } from './Sidebar';

export const MainLayout = () => {
  return (
    <ChildrenProvider>
      {/* The window never scrolls: the sidebar stays put and only the main content scrolls. */}
      <Box sx={{ display: 'flex', height: '100vh', overflow: 'hidden', bgcolor: 'background.default' }}>
        <Sidebar />
        <Box component="main" sx={{ flexGrow: 1, minWidth: 0, p: 0, overflow: 'auto' }}>
          <Outlet />
        </Box>
      </Box>
    </ChildrenProvider>
  );
};