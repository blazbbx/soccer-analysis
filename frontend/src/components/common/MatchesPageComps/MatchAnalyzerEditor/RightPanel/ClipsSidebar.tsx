import React from 'react';
import { Box, Typography, Stack, useTheme } from '@mui/material';
import ContentCutIcon from '@mui/icons-material/ContentCut';
import { useClip } from '../../../../../context/ClipContext';
import { ClipCard } from './ClipCard';

export const ClipsSidebar: React.FC = () => {
  const { clips, shakeUnsavedTrigger } = useClip();
  const theme = useTheme();

  const hasUnsaved = clips.some((c) => c.isEditing);

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        flex: 1,
        minHeight: 0,
        bgcolor: theme.palette.background.paper,
        borderLeft: `1px solid ${theme.palette.divider}`,
      }}
    >
      {/* Fejléc */}
      <Box
        sx={{
          p: 2,
          borderBottom: `1px solid ${theme.palette.divider}`,
          display: 'flex',
          alignItems: 'center',
          gap: 1,
        }}
      >
        <ContentCutIcon
          sx={{ color: '#8b5cf6', fontSize: '18px', transform: 'rotate(270deg)' }}
        />
        <Typography
          sx={{
            color: theme.palette.text.secondary,
            fontWeight: 'bold',
            fontSize: '14px',
            textTransform: 'uppercase',
          }}
        >
          Klippek ({clips.length})
        </Typography>
      </Box>

      {/* Klipek listája */}
      <Box sx={{ flex: 1, overflowY: 'auto', p: 2 }}>
        <Stack spacing={2}>
          {clips.length === 0 ? (
            <Typography
              sx={{
                color: theme.palette.text.secondary,
                fontSize: '13px',
                fontStyle: 'italic',
                textAlign: 'center',
              }}
            >
              Még nincsenek klipek.
            </Typography>
          ) : (
            clips.map((clip) => (
              <ClipCard
                key={clip.id}
                clip={clip}
                hasUnsaved={hasUnsaved}
                shakeTrigger={shakeUnsavedTrigger}
              />
            ))
          )}
        </Stack>
      </Box>
    </Box>
  );
};
