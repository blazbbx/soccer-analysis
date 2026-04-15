import React, { useEffect, useRef } from 'react';
import { Box, useTheme, type SvgIconProps } from '@mui/material';
import { useVideoPlayer } from '../../../../../context/VideoPlayerContext';
import { LABEL_ITEMS } from '../../../../../constants/labels';

interface LabelsTrackProps {
  isEditor: boolean;
}

export const LabelsTrack: React.FC<LabelsTrackProps> = ({ isEditor }) => {
  const { setIsPlaying, isPlaying, currentTime, duration, labels, addLabel } = useVideoPlayer();
  const theme = useTheme();

  const currentTimeRef = useRef(currentTime);
  useEffect(() => {
    currentTimeRef.current = currentTime;
  }, [currentTime]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      const target = event.target as HTMLInputElement;

      const isTypingInInput =
        target.tagName === 'TEXTAREA' ||
        target.isContentEditable ||
        (target.tagName === 'INPUT' && !['range', 'button', 'checkbox', 'radio'].includes(target.type));

      if (isTypingInInput) return;

      const pressedKey = event.key.toLowerCase();

      if (pressedKey === ' ' || event.code === 'Space') {
        event.preventDefault();
        setIsPlaying(!isPlaying);
        return;
      }

      if (!isEditor) return;

      const matchedConfig = LABEL_ITEMS.find((item) => item.hotkey.toLowerCase() === pressedKey);

      if (matchedConfig) {
        event.preventDefault();
        addLabel({
          config: matchedConfig,
          time: currentTimeRef.current,
        });
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [addLabel, isEditor, isPlaying, setIsPlaying]);

  const maxDuration = duration > 0 ? duration : 5400;

  return (
    <Box sx={{ display: 'flex', width: '100%', height: '40px', backgroundColor: theme.palette.background.default, border: `1px solid ${theme.palette.divider}` }}>
      <Box sx={{ flex: 1, position: 'relative', overflow: 'hidden' }}>
        {labels.map((label) => {
          const leftPercent = (label.time / maxDuration) * 100;
          return (
            <Box
              key={label.id}
              sx={{
                position: 'absolute',
                top: '50%',
                left: `${leftPercent}%`,
                transform: 'translate(-50%, -50%)',
                cursor: 'pointer',
                '&:hover': { transform: 'translate(-50%, -50%) scale(1.2)' }
              }}
              title={`${label.config.event} (${Math.floor(label.time)}s)`}
            >
              {React.cloneElement(label.config.icon as React.ReactElement<SvgIconProps>, { sx: { color: label.config.color, fontSize: '20px' } })}
            </Box>
          );
        })}
      </Box>
    </Box>
  );
};