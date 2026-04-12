import React from 'react';
import { Box, Typography, Stack, IconButton, Button, useTheme } from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import CloseIcon from '@mui/icons-material/Close';
import CreateIcon from '@mui/icons-material/Create';
import ArrowOutwardIcon from '@mui/icons-material/ArrowOutward';
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked';
import UndoIcon from '@mui/icons-material/Undo';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import { useVideoPlayer } from '../../../../../context/VideoPlayerContext';

const DRAW_COLORS = ['#f44336', '#ff9800', '#ffeb3b', '#4caf50', '#2196f3', '#9c27b0', '#ffffff'];

export const DrawingToolsPanel: React.FC = () => {
  const { activeDrawTool, setActiveDrawTool, activeDrawColor, setActiveDrawColor, triggerUndo, triggerClear } = useVideoPlayer();
  const theme = useTheme();

  return (
    <Box sx={{ p: 2, borderBottom: `1px solid ${theme.palette.divider}`, display: 'flex', flexDirection: 'column', gap: 2 }}>
      
      {/* Fejléc */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
        <EditIcon sx={{ color: '#00e676', fontSize: '18px' }} />
        <Typography sx={{ color: theme.palette.text.secondary, fontSize: '14px', fontWeight: 'bold' }}>
          Rajzeszközök
        </Typography>
      </Box>

      {/* Eszközök (Tools) */}
      <Stack direction="row" spacing={1}>
        {[
          { id: 'none', icon: <CloseIcon fontSize="small" /> },
          { id: 'pen', icon: <CreateIcon fontSize="small" /> },
          { id: 'arrow', icon: <ArrowOutwardIcon fontSize="small" /> },
          { id: 'circle', icon: <RadioButtonUncheckedIcon fontSize="small" /> }
        ].map((tool) => (
          <IconButton
            key={tool.id}
            onClick={() => setActiveDrawTool(tool.id as any)}
            sx={{
              backgroundColor: activeDrawTool === tool.id ? '#00e676' : theme.palette.secondary.main,
              color: activeDrawTool === tool.id ? '#fff' : theme.palette.text.secondary,
              borderRadius: '8px',
              width: 44,
              height: 40,
              '&:hover': {
                backgroundColor: activeDrawTool === tool.id ? '#00c853' : theme.palette.action.hover,
              }
            }}
          >
            {tool.icon}
          </IconButton>
        ))}
      </Stack>

      {/* Színek (Colors) */}
      <Stack direction="row" spacing={1.5} sx={{ mt: 1 }}>
        {DRAW_COLORS.map((color) => (
          <Box
            key={color}
            onClick={() => setActiveDrawColor(color)}
            sx={{
              width: 24,
              height: 24,
              backgroundColor: color,
              borderRadius: '50%',
              cursor: 'pointer',
              border: activeDrawColor === color ? `3px solid ${theme.palette.text.primary}` : `1px solid ${theme.palette.text.secondary}`,
              boxShadow: activeDrawColor === color ? `0 0 0 2px ${theme.palette.background.paper} inset` : 'none',
              '&:hover': { opacity: 0.8 }
            }}
          />
        ))}
      </Stack>

      {/* Akció gombok (Undo / Clear) */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 1 }}>
        <Button
          startIcon={<UndoIcon />}
          onClick={triggerUndo}
          sx={{ color: theme.palette.text.secondary, textTransform: 'none', '&:hover': { color: theme.palette.text.primary, bgcolor: theme.palette.action.hover } }}
        >
          Vissza
        </Button>
        <Button
          color="error"
          startIcon={<DeleteOutlineIcon />}
          onClick={triggerClear}
          sx={{  textTransform: 'none' }}
        >
          Törlés
        </Button>
      </Box>
    </Box>
  );
};