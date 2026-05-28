import React from 'react';
import { Box, Typography, Stack, IconButton, Button, Chip, useTheme } from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import CloseIcon from '@mui/icons-material/Close';
import CreateIcon from '@mui/icons-material/Create';
import ArrowOutwardIcon from '@mui/icons-material/ArrowOutward';
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked';
import UndoIcon from '@mui/icons-material/Undo';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import PersonPinIcon from '@mui/icons-material/PersonPin';
import { useRecording } from '../../../../context/RecordingContext';

const DRAW_COLORS = ['#f44336', '#ff9800', '#ffeb3b', '#4caf50', '#2196f3', '#9c27b0', '#ffffff'];

export const DrawingToolsPanel: React.FC = () => {
  const {
    activeDrawTool,
    setActiveDrawTool,
    activeDrawColor,
    setActiveDrawColor,
    triggerUndo,
    triggerClear,
    followPlayerMode,
    setFollowPlayerMode,
    selectedPlayerId,
    setSelectedPlayerId,
    undoLastDrawing,
    clearDrawings,
  } = useRecording();

  const theme = useTheme();

  const handleFollowPlayerToggle = () => {
    if (followPlayerMode) {
      setFollowPlayerMode(false);
      setSelectedPlayerId(null);
    } else {
      setFollowPlayerMode(true);
    }
  };

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
      <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
        {[
          { id: 'none', icon: <CloseIcon fontSize="small" /> },
          { id: 'pen', icon: <CreateIcon fontSize="small" /> },
          { id: 'arrow', icon: <ArrowOutwardIcon fontSize="small" /> },
          { id: 'circle', icon: <RadioButtonUncheckedIcon fontSize="small" /> },
        ].map((tool) => (
          <IconButton
            key={tool.id}
            onClick={() => setActiveDrawTool(tool.id as 'none' | 'pen' | 'arrow' | 'circle')}
            sx={{
              backgroundColor: activeDrawTool === tool.id ? '#00e676' : theme.palette.secondary.main,
              color: activeDrawTool === tool.id ? '#fff' : theme.palette.text.secondary,
              borderRadius: '8px',
              width: 44,
              height: 40,
              '&:hover': {
                backgroundColor: activeDrawTool === tool.id ? '#00c853' : theme.palette.action.hover,
              },
            }}
          >
            {tool.icon}
          </IconButton>
        ))}

        {/* Follow Player toggle */}
        <IconButton
          onClick={handleFollowPlayerToggle}
          sx={{
            backgroundColor: followPlayerMode ? '#2196f3' : theme.palette.secondary.main,
            color: followPlayerMode ? '#fff' : theme.palette.text.secondary,
            borderRadius: '8px',
            width: 44,
            height: 40,
            '&:hover': {
              backgroundColor: followPlayerMode ? '#1976d2' : theme.palette.action.hover,
            },
          }}
        >
          <PersonPinIcon fontSize="small" />
        </IconButton>
      </Stack>

      {/* Selected player chip */}
      {followPlayerMode && (
        <Box>
          {selectedPlayerId === null ? (
            <Typography sx={{ fontSize: '12px', color: theme.palette.text.secondary }}>
              Kattints egy játékosra a kiválasztáshoz
            </Typography>
          ) : (
            <Chip
              label={`Játékos #${selectedPlayerId}`}
              onDelete={() => setSelectedPlayerId(null)}
              size="small"
              sx={{
                backgroundColor: '#2196f3',
                color: '#fff',
                '& .MuiChip-deleteIcon': { color: 'rgba(255,255,255,0.7)' },
              }}
            />
          )}
        </Box>
      )}

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
              '&:hover': { opacity: 0.8 },
            }}
          />
        ))}
      </Stack>

      {/* Akció gombok (Undo / Clear) */}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 1 }}>
        <Button
          startIcon={<UndoIcon />}
          onClick={() => { triggerUndo(); undoLastDrawing(); }}
          sx={{ color: theme.palette.text.secondary, textTransform: 'none', '&:hover': { color: theme.palette.text.primary, bgcolor: theme.palette.action.hover } }}
        >
          Vissza
        </Button>
        <Button
          color="error"
          startIcon={<DeleteOutlineIcon />}
          onClick={() => { triggerClear(); clearDrawings(); }}
          sx={{ textTransform: 'none' }}
        >
          Törlés
        </Button>
      </Box>
    </Box>
  );
};
