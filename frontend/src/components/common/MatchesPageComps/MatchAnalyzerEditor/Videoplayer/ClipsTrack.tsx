import React from 'react';
import { Box, Slider, useTheme } from '@mui/material';
import { useVideoPlayer } from '../../../../../context/VideoPlayerContext';

export const ClipsTrack: React.FC = () => {
  const { duration, clips, updateClipTimes } = useVideoPlayer();
  const theme = useTheme();

  const maxDuration = duration > 0 ? duration : 5400;

  return (
    <Box sx={{ display: 'flex', width: '100%', height: '40px', backgroundColor: theme.palette.background.paper, border: `1px solid ${theme.palette.divider}` }}>
      {/* Track Test */}
      <Box sx={{ flex: 1, position: 'relative', padding: '0 16px', display: 'flex', alignItems: 'center' }}>
        {clips.map((clip) => (
          <Slider
            key={clip.id}
            value={[clip.startTime, clip.endTime]}
            min={0}
            max={maxDuration}
            disabled={!clip.isEditing}
            
            onChange={(_e, newValue) => {
              const [start, end] = newValue as number[];
              updateClipTimes(clip.id, start, end);
            }}
            sx={{
              position: 'absolute',
              width: 'calc(100% - 32px)', 
              color: clip.color,
              
              
              '& .MuiSlider-track': {
                height: '100%', 
                backgroundColor: clip.isEditing ? `${clip.color}40` : `${clip.color}15`, 
                borderTop: `2px solid ${clip.isEditing ? clip.color : theme.palette.text.secondary}`,
                borderBottom: `2px solid ${clip.isEditing ? clip.color : theme.palette.text.secondary}`,
              },
              
              
              '& .MuiSlider-rail': {
                opacity: 0, 
              },
              
              
              '& .MuiSlider-thumb': {
                display: clip.isEditing ? 'flex' : 'none',
                height: '100%',
                width: '8px',
                borderRadius: '2px',
                backgroundColor: clip.color,
                '&:hover, &.Mui-focusVisible': {
                  boxShadow: `0px 0px 0px 8px ${clip.color}30`,
                },
              },

              '&.Mui-disabled': {
                color: clip.color, 
              }
            }}
          />
        ))}
      </Box>
    </Box>
  );
};