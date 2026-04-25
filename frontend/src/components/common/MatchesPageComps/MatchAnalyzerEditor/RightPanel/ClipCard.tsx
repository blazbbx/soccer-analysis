import React, { useEffect, useRef, useState } from 'react';
import {
  Box,
  Typography,
  Stack,
  IconButton,
  Button,
  TextField,
  useTheme,
} from '@mui/material';
import { keyframes } from '@mui/system';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import { useVideoPlayer } from '../../../../../context/VideoPlayerContext';
import { useClip } from '../../../../../context/ClipContext';
import { formatTime } from '../../../../../utils/timeFormat';
import type { ClipItem } from '../../../../../context/ClipContext';

const clipShake = keyframes`
  0%   { transform: translateX(0); }
  15%  { transform: translateX(-6px); }
  30%  { transform: translateX(6px); }
  45%  { transform: translateX(-5px); }
  60%  { transform: translateX(5px); }
  75%  { transform: translateX(-3px); }
  90%  { transform: translateX(3px); }
  100% { transform: translateX(0); }
`;

interface ClipCardProps {
  clip: ClipItem;
  hasUnsaved: boolean;
  shakeTrigger: number;
}

export const ClipCard: React.FC<ClipCardProps> = ({ clip, hasUnsaved, shakeTrigger }) => {
  const { setCurrentTime } = useVideoPlayer();
  const { updateClipName, toggleClipEditMode, deleteClip } = useClip();
  const theme = useTheme();
  const [isShaking, setIsShaking] = useState(false);
  const prevTriggerRef = useRef(shakeTrigger);

  useEffect(() => {
    if (shakeTrigger === prevTriggerRef.current) return;
    prevTriggerRef.current = shakeTrigger;
    if (!clip.isEditing) return;
    setIsShaking(true);
    const timer = setTimeout(() => setIsShaking(false), 1500);
    return () => clearTimeout(timer);
  }, [shakeTrigger, clip.isEditing]);

  const borderColor = isShaking
    ? '#f44336'
    : clip.isEditing
    ? clip.color
    : theme.palette.divider;

  const borderWidth = isShaking ? '2px' : '1px';

  return (
    <Box
      sx={{
        backgroundColor: theme.palette.background.default,
        borderRadius: 1,
        p: 2,
        border: `${borderWidth} solid ${borderColor}`,
        animation: isShaking ? `${clipShake} 0.6s ease` : 'none',
      }}
    >
      {clip.isEditing ? (
        <Stack spacing={1.5}>
          {isShaking && (
            <Typography sx={{ color: '#f44336', fontSize: '12px', fontWeight: 500 }}>
              Mentsd el a klipet, mielőtt újat hozol létre!
            </Typography>
          )}
          <TextField
            size="small"
            value={clip.name}
            onChange={(e) => updateClipName(clip.id, e.target.value)}
            placeholder="Klip neve..."
            autoFocus={!isShaking}
            sx={{
              input: { color: theme.palette.text.primary, fontSize: '14px' },
              '& .MuiOutlinedInput-root': {
                '& fieldset': { color: theme.palette.divider },
                '&:hover fieldset': { borderColor: clip.color },
                '&.Mui-focused fieldset': { borderColor: clip.color },
              },
            }}
          />
          <Stack direction="row" spacing={1} justifyContent="flex-end">
            <Button size="small" color="error" onClick={() => deleteClip(clip.id)}>
              Törlés
            </Button>
            <Button
              size="small"
              variant="contained"
              sx={{
                bgcolor: clip.color,
                color: '#fff',
                '&:hover': { bgcolor: clip.color },
              }}
              onClick={() => toggleClipEditMode(clip.id, false)}
            >
              Mentés
            </Button>
          </Stack>
        </Stack>
      ) : (
        <Stack spacing={1}>
          <Typography
            sx={{ color: theme.palette.text.primary, fontSize: '14px', fontWeight: 500 }}
          >
            {clip.name}
          </Typography>
          <Box
            sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}
          >
            <Typography
              onClick={() => setCurrentTime(clip.startTime)}
              sx={{
                color: theme.palette.text.secondary,
                fontSize: '13px',
                fontFamily: 'monospace',
                cursor: 'pointer',
                transition: 'color 0.2s',
                '&:hover': { color: clip.color },
              }}
            >
              {formatTime(clip.startTime)} → {formatTime(clip.endTime)}
            </Typography>
            <Box>
              <IconButton
                size="small"
                disabled={hasUnsaved}
                onClick={() => toggleClipEditMode(clip.id, true)}
                sx={{ color: hasUnsaved ? theme.palette.action.disabled : '#00e676' }}
              >
                <EditIcon fontSize="small" />
              </IconButton>
              <IconButton
                size="small"
                onClick={() => deleteClip(clip.id)}
                sx={{ color: '#f44336' }}
              >
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Box>
          </Box>
        </Stack>
      )}
    </Box>
  );
};
