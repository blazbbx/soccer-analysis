import { useMemo, useState } from 'react';
import { Box, Slider, useTheme } from '@mui/material';
import { useMediaTime, usePlaybackCommands, usePlaybackState } from '../../../../player/hooks';
import { formatTime } from '../../../../utils/timeFormat';

export const TimelineBar: React.FC = () => {
  const mediaTime = useMediaTime(0.25);
  const { duration } = usePlaybackState();
  const playback = usePlaybackCommands();
  const theme = useTheme();

  // Húzás közben a csúszka a mutatót követi, nem a videót, amely a seek-ek miatt le van maradva.
  // Elengedés után a húzott érték addig marad, amíg a videó új időt nem jelent.
  const [drag, setDrag] = useState<{ value: number; releasedAt: number | null } | null>(null);
  const shownTime =
    drag && (drag.releasedAt === null || drag.releasedAt === mediaTime) ? drag.value : mediaTime;

  const maxDuration = duration > 0 ? duration : 5400;

  const marks = useMemo(() => {
    const result = [];
    for (let i = 300; i <= maxDuration; i += 300) {
      result.push({ value: i, label: formatTime(i) });
    }
    return result;
  }, [maxDuration]);

  const sliderSx = useMemo(() => ({
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
    '& .MuiSlider-rail': { opacity: 0 },
    '& .MuiSlider-track': { opacity: 0 },
    '& .MuiSlider-markLabel': {
      color: theme.palette.text.secondary,
      fontSize: '11px',
      fontFamily: 'monospace',
      top: 12,
    },
  }), [theme]);

  const handleSliderChange = (_event: Event, newValue: number | number[]) => {
    const value = newValue as number;
    setDrag({ value, releasedAt: null });
    playback.seek(value);
  };

  const handleSliderCommitted = () => {
    setDrag((d) => (d ? { ...d, releasedAt: mediaTime } : d));
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
        position: 'relative',
        overflow: 'visible',
      }}
    >
      <Slider
        value={shownTime}
        min={0}
        max={maxDuration}
        onChange={handleSliderChange}
        onChangeCommitted={handleSliderCommitted}
        marks={marks}
        sx={sliderSx}
      />
    </Box>
  );
};
