import { Box, IconButton, Stack, Slider, Typography, Button, useTheme } from '@mui/material';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import PauseIcon from '@mui/icons-material/Pause';
import FastRewindIcon from '@mui/icons-material/Replay5';
import FastForwardIcon from '@mui/icons-material/Forward5';
import VolumeUpIcon from '@mui/icons-material/VolumeUp';
import VolumeOffIcon from '@mui/icons-material/VolumeOff';
import FiberManualRecordIcon from '@mui/icons-material/FiberManualRecord';
import StopIcon from '@mui/icons-material/Stop';
import { useVideoPlayer } from '../../../../../context/VideoPlayerContext';
import { useRecording } from '../../../../../context/RecordingContext';
import { speedOptions } from '../../../../../constants/speedOptions';
import { formatTime } from '../../../../../utils/timeFormat';

export const PlaybackControls = () => {
  const { isPlaying, setIsPlaying, playbackRate, setPlaybackRate, volume, setVolume, currentTime, handleSkip } = useVideoPlayer();
  const { isRecording, startRecording, stopRecording } = useRecording();

  const theme = useTheme();

  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        backgroundColor: theme.palette.background.paper,
        padding: '8px 16px',
        border: `1px solid ${theme.palette.divider}`,
        borderTop: 'none',
      }}
    >
      {/* Bal oldal: Lejátszás vezérlők */}
      <Stack direction="row" spacing={1} alignItems="center">
        <IconButton size="small" sx={{ color: theme.palette.text.secondary, '&:hover': { color: theme.palette.text.primary } }} onClick={() => handleSkip(-5)}>
          <FastRewindIcon fontSize="small" />
        </IconButton>

        <IconButton
          onClick={() => setIsPlaying(!isPlaying)}
          sx={{
            backgroundColor: '#00e676',
            color: '#000',
            '&:hover': { backgroundColor: '#00c853' },
            width: 25,
            height: 25,
          }}
        >
          {isPlaying ? <PauseIcon /> : <PlayArrowIcon />}
        </IconButton>

        <IconButton size="small" sx={{ color: theme.palette.text.secondary, '&:hover': { color: theme.palette.text.primary } }} onClick={() => handleSkip(5)}>
          <FastForwardIcon fontSize="small" />
        </IconButton>

        {/* Sebesség gombok */}
        <Stack direction="row" spacing={0.5} sx={{ ml: 3 }}>
          {speedOptions.map((rate) => (
            <Button
              key={rate}
              onClick={() => setPlaybackRate(rate)}
              sx={{
                minWidth: 'auto',
                padding: '2px 8px',
                color: playbackRate === rate ? '#fff' : theme.palette.text.secondary,
                backgroundColor: playbackRate === rate ? '#00e676' : 'transparent',
                borderRadius: 1,
                textTransform: 'none',
                fontWeight: playbackRate === rate ? 'bold' : 'normal',
                '&:hover': {
                  backgroundColor: playbackRate === rate ? '#00c853' : theme.palette.action.hover,
                },
              }}
            >
              {rate}x
            </Button>
          ))}
        </Stack>

        {/* Record gomb */}
        <IconButton
          onClick={isRecording ? stopRecording : startRecording}
          sx={{
            ml: 1,
            backgroundColor: isRecording ? '#f44336' : 'transparent',
            color: isRecording ? '#fff' : '#f44336',
            border: `1px solid #f44336`,
            borderRadius: '8px',
            width: 36,
            height: 36,
            animation: isRecording ? 'pulse 1.5s ease-in-out infinite' : 'none',
            '@keyframes pulse': {
              '0%, 100%': { opacity: 1 },
              '50%': { opacity: 0.5 },
            },
            '&:hover': {
              backgroundColor: isRecording ? '#d32f2f' : 'rgba(244,67,54,0.1)',
            },
          }}
        >
          {isRecording ? <StopIcon fontSize="small" /> : <FiberManualRecordIcon fontSize="small" />}
        </IconButton>
      </Stack>

      {/* Jobb oldal: Hangerő és Idő */}
      <Stack direction="row" spacing={2} alignItems="center">
        <Stack direction="row" spacing={1} alignItems="center" sx={{ width: 120 }}>
          <IconButton
            size="small"
            sx={{ color: theme.palette.text.secondary, '&:hover': { color: theme.palette.text.primary } }}
            onClick={() => setVolume(volume === 0 ? 1 : 0)}
          >
            {volume === 0 ? <VolumeOffIcon fontSize="small" /> : <VolumeUpIcon fontSize="small" />}
          </IconButton>
          <Slider
            size="small"
            value={volume}
            min={0}
            max={1}
            step={0.01}
            onChange={(_, newValue) => setVolume(newValue)}
            sx={{
              color: theme.palette.text.secondary,
              '& .MuiSlider-thumb': { width: 12, height: 12 },
            }}
          />
        </Stack>
        <Typography sx={{ color: theme.palette.text.secondary, fontFamily: 'monospace', fontSize: '14px' }}>
          {formatTime(currentTime)}
        </Typography>
      </Stack>
    </Box>
  );
};
