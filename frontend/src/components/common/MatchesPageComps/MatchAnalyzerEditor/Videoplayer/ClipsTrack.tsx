import React, { useRef } from 'react';
import { Box, Slider, useTheme } from '@mui/material';
import { useVideoPlayer } from '../../../../../context/VideoPlayerContext';
import { useClip } from '../../../../../context/ClipContext';
import { formatTime } from '../../../../../utils/timeFormat';

export const ClipsTrack: React.FC = () => {
  const { duration, setCurrentTime } = useVideoPlayer();
  const { clips, updateClipTimes } = useClip();
  const theme = useTheme();
  const seekTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastSeekTimeRef = useRef<number | null>(null);

  const maxDuration = duration > 0 ? duration : 5400;

  const debouncedSeek = (time: number) => {
    lastSeekTimeRef.current = time;
    if (seekTimerRef.current) clearTimeout(seekTimerRef.current);
    seekTimerRef.current = setTimeout(() => setCurrentTime(time), 120);
  };

  return (
    <Box sx={{ display: 'flex', width: '100%', height: '40px', backgroundColor: theme.palette.background.paper, border: `1px solid ${theme.palette.divider}` }}>
      {/* Track Test */}
      <Box sx={{ flex: 1, position: 'relative', display: 'flex', alignItems: 'center' }}>
        {clips.map((clip) => (
          <Slider
            key={clip.id}
            value={[clip.startTime, clip.endTime]}
            min={0}
            max={maxDuration}
            disabled={!clip.isEditing}
            valueLabelDisplay="auto"
            valueLabelFormat={(v) => formatTime(v)}
            onChange={(_e, newValue, activeThumb) => {
              const [start, end] = newValue as number[];
              updateClipTimes(clip.id, start, end);
              debouncedSeek(activeThumb === 0 ? start : end);
            }}
            onChangeCommitted={() => {
              if (seekTimerRef.current) clearTimeout(seekTimerRef.current);
              if (lastSeekTimeRef.current !== null) setCurrentTime(lastSeekTimeRef.current);
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
                width: '3px',
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