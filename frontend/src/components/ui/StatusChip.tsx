import { Chip, useTheme, alpha, type ChipProps, type Theme } from '@mui/material';

export type StatusTone = 'success' | 'warning' | 'error' | 'info' | 'neutral';

// Single source of truth for tone -> color, shared with any component that
// needs the color without rendering a full StatusChip (e.g. MatchCard's status dot).
export const getStatusToneColor = (theme: Theme, tone: StatusTone) =>
  tone === 'neutral' ? theme.palette.text.secondary : theme.palette[tone].main;

interface StatusChipProps extends Omit<ChipProps, 'color'> {
  tone: StatusTone;
}

export const StatusChip = ({ tone, sx, ...rest }: StatusChipProps) => {
  const theme = useTheme();
  const color = getStatusToneColor(theme, tone);

  return (
    <Chip
      size="small"
      {...rest}
      sx={{
        bgcolor: alpha(color, 0.12),
        color,
        fontWeight: 600,
        border: 'none',
        '& .MuiChip-icon': { color },
        ...sx,
      }}
    />
  );
};
