import { Box, Typography } from '@mui/material';
import OndemandVideoIcon from '@mui/icons-material/OndemandVideo';
import { useNavigate } from 'react-router-dom';
import type { ChatClipSummary } from '../../api/generated/model';
import { FROM_CHAT_STATE } from './chatNavigation';

interface SharedClipCardProps {
  clip: ChatClipSummary;
}

/** A clip shared in the team chat. Opens the clip viewer on click. */
export const SharedClipCard = ({ clip }: SharedClipCardProps) => {
  const navigate = useNavigate();
  const open = () => {
    if (clip.id) navigate(`/clip/${clip.id}`, { state: FROM_CHAT_STATE });
  };

  return (
    <Box
      role="link"
      tabIndex={0}
      onClick={open}
      onKeyDown={e => {
        if (e.key === 'Enter') open();
      }}
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 1.5,
        px: 2,
        py: 1,
        borderRadius: 2,
        cursor: 'pointer',
        transition: 'background-color 0.2s ease',
        '&:hover': { bgcolor: 'action.hover' },
      }}
    >
      <Box
        sx={{
          width: 36,
          height: 36,
          borderRadius: 1.5,
          bgcolor: 'rgba(20, 184, 166, 0.1)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
        }}
      >
        <OndemandVideoIcon sx={{ fontSize: 18, color: '#14b8a6' }} />
      </Box>
      <Box sx={{ flexGrow: 1, overflow: 'hidden' }}>
        <Typography variant="body2" fontWeight={500} noWrap>
          {clip.name ?? '—'}
        </Typography>
        {clip.matchDisplayName && (
          <Typography variant="caption" color="text.secondary" noWrap display="block">
            {clip.matchDisplayName}
          </Typography>
        )}
      </Box>
    </Box>
  );
};
