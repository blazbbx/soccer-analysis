import React from 'react';
import { Box, Slider, useTheme } from '@mui/material';
import { useVideoPlayer } from '../../../../../context/VideoPlayerContext';
import { formatTime } from '../../../../../utils/timeFormat';

export const TimelineBar: React.FC = () => {
  const { currentTime, duration, setCurrentTime } = useVideoPlayer();
  const theme = useTheme();

  
  const generateMarks = () => {
    const marks = [];
    
    const maxDuration = duration > 0 ? duration : 5400; 
    
    for (let i = 300; i <= maxDuration; i += 300) {
      marks.push({
        value: i,
        label: formatTime(i),
      });
    }
    return marks;
  };

  const handleSliderChange = (_event: Event, newValue: number | number[]) => {
    setCurrentTime(newValue as number);
  };

  return (
    <Box
      sx={{
        width: '100%',
        height: '40px',
        backgroundColor: theme.palette.background.paper, 
        border: `1px solid ${theme.palette.divider}`,
        borderTop: 'none',
        display: 'flex',
        alignItems: 'center',
        padding: '0 16px', 
        position: 'relative',
      }}
    >
      <Slider
        value={currentTime}
        min={0}
        max={duration > 0 ? duration : 5400}
        onChange={handleSliderChange}
        marks={generateMarks()}
        valueLabelDisplay="auto"
        valueLabelFormat={formatTime}
        sx={{
          color: '#00e676', 
          padding: '0px',
          height: '100%',
          
          
          '& .MuiSlider-thumb': {
            width: 4,
            height: '100%',
            borderRadius: '2px',
            backgroundColor: '#00e676', 
            '&:hover, &.Mui-focusVisible': {
              boxShadow: `0px 0px 0px 8px ${theme.palette.action.hover}`,
            },
          },
          
          
          '& .MuiSlider-rail': {
            opacity: 0, 
          },
          '& .MuiSlider-track': {
            opacity: 0, 
          },
          
          
          '& .MuiSlider-mark': {
            backgroundColor: theme.palette.divider,
            height: 8,
            width: 1,
            top: 0, 
          },
          
          
          '& .MuiSlider-markLabel': {
            color: theme.palette.text.secondary,
            fontSize: '11px',
            fontFamily: 'monospace',
            top: 12, 
          },
        }}
      />
    </Box>
  );
};