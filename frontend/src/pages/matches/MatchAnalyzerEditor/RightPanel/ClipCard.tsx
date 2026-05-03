import React, { useState, useEffect } from 'react';
import {
  Box,
  Chip,
  CircularProgress,
  Dialog,
  DialogContent,
  DialogTitle,
  IconButton,
  Stack,
  Typography,
} from '@mui/material';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutline';
import CloseIcon from '@mui/icons-material/Close';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import ShareIcon from '@mui/icons-material/Share';
import { ClipShareDialog } from './ClipShareDialog';
import { useGetRenderedClipDownloadUrl } from '../../../../api/generated/clip-controller/clip-controller';
import type { ClipResponse } from '../../../../api/generated/model';
import { useClipPolling } from '../../hooks/useClipPolling';
import { PrimaryButton } from '../../../../components/ui/PrimaryButton';

interface ClipCardProps {
  clip: ClipResponse;
  onDelete?: () => void;
  isDeleting?: boolean;
  isEditor?: boolean;
}

const STATUS_COLORS: Record<string, 'success' | 'info' | 'warning' | 'error' | 'default'> = {
  COMPLETED: 'success',
  PROCESSING: 'info',
  QUEUED: 'warning',
  PENDING_UPLOAD: 'default',
  FAILED: 'error',
};

export const ClipCard = React.memo(({ clip, onDelete, isDeleting = false, isEditor }: ClipCardProps) => {
  const [viewerOpen, setViewerOpen] = useState(false);
  const [shareOpen, setShareOpen] = useState(false);
  const [thumbnailDataUrl, setThumbnailDataUrl] = useState<string | null>(null);
  const [hovered, setHovered] = useState(false);

  const liveClip = useClipPolling(clip);

  const isCompleted = liveClip.renderStatus === 'COMPLETED';

  const { data: urlData, isLoading: urlLoading } = useGetRenderedClipDownloadUrl(liveClip.id!, {
    query: { enabled: isCompleted && !!liveClip.id },
  });

  const videoUrl: string | undefined = urlData
    ? Object.values(urlData as unknown as Record<string, string>)[0]
    : undefined;

  useEffect(() => {
    if (!videoUrl || thumbnailDataUrl) return;
    const video = document.createElement('video');
    video.crossOrigin = 'anonymous';
    video.preload = 'metadata';
    video.src = videoUrl;
    video.onloadedmetadata = () => { video.currentTime = 0.5; };
    video.onseeked = () => {
      const canvas = document.createElement('canvas');
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      canvas.getContext('2d')?.drawImage(video, 0, 0);
      setThumbnailDataUrl(canvas.toDataURL('image/jpeg', 0.7));
      video.src = '';
    };
  }, [videoUrl, thumbnailDataUrl]);

  return (
    <>
      <Box
        sx={{
          position: 'relative',
          borderRadius: 1,
          overflow: 'hidden',
          border: '1px solid',
          borderColor: 'divider',
          bgcolor: 'background.default',
        }}
        onMouseEnter={() => setHovered(true)}
        onMouseLeave={() => setHovered(false)}
      >
        {isDeleting && (
          <Box
            sx={{
              position: 'absolute', inset: 0, zIndex: 3,
              bgcolor: 'rgba(0,0,0,0.5)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}
          >
            <CircularProgress size={28} sx={{ color: '#fff' }} />
          </Box>
        )}
        {onDelete && !isDeleting && isEditor !== false && (
          <IconButton
            size="small"
            onClick={(e) => { e.stopPropagation(); onDelete(); }}
            sx={{
              position: 'absolute', top: 4, right: 4, zIndex: 2,
              color: 'error.main',
              bgcolor: 'rgba(0,0,0,0.55)',
              opacity: hovered ? 1 : 0,
              transition: 'opacity 0.15s',
              '&:hover': { bgcolor: 'error.main', color: '#fff' },
            }}
          >
            <DeleteOutlineIcon fontSize="small" />
          </IconButton>
        )}
        {isCompleted && isEditor !== false && (
          <IconButton
            size="small"
            onClick={(e) => { e.stopPropagation(); setShareOpen(true); }}
            sx={{
              position: 'absolute', top: 4, right: 36, zIndex: 2,
              color: 'primary.main',
              bgcolor: 'rgba(0,0,0,0.55)',
              opacity: hovered ? 1 : 0,
              transition: 'opacity 0.15s',
              '&:hover': { bgcolor: 'primary.main', color: '#fff' },
            }}
          >
            <ShareIcon fontSize="small" />
          </IconButton>
        )}
        {/* Thumbnail */}
        <Box sx={{ position: 'relative', bgcolor: '#000', minHeight: 90 }}>
          {isCompleted && videoUrl ? (
            <>
              {thumbnailDataUrl
                ? <Box component="img" src={thumbnailDataUrl} sx={{ width: '100%', maxHeight: 120, display: 'block', objectFit: 'cover' }} />
                : <Box sx={{ height: 90, display: 'flex', alignItems: 'center', justifyContent: 'center' }}><CircularProgress size={24} /></Box>
              }
              <Box
                sx={{
                  position: 'absolute', inset: 0,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  background: 'rgba(0,0,0,0.25)',
                  cursor: 'pointer',
                  '&:hover': { background: 'rgba(0,0,0,0.45)' },
                }}
                onClick={() => setViewerOpen(true)}
              >
                <PlayCircleOutlineIcon sx={{ fontSize: 40, color: '#fff' }} />
              </Box>
            </>
          ) : (
            <Box
              sx={{
                height: 90,
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}
            >
              {urlLoading
                ? <CircularProgress size={24} />
                : <PlayCircleOutlineIcon sx={{ fontSize: 36, color: 'text.disabled' }} />
              }
            </Box>
          )}
        </Box>

        {/* Info */}
        <Stack spacing={0.5} sx={{ p: 1.5 }}>
          <Typography sx={{ fontSize: '13px', fontWeight: 500, color: 'text.primary' }}>
            {liveClip.name || 'Unnamed clip'}
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <Chip
              label={liveClip.renderStatus ?? 'UNKNOWN'}
              size="small"
              color={STATUS_COLORS[liveClip.renderStatus ?? ''] ?? 'default'}
              sx={{ fontSize: '11px', height: 20 }}
            />
            {isCompleted && videoUrl && (
              <PrimaryButton
                size="small"
                onClick={() => setViewerOpen(true)}
                sx={{ py: 0.25, px: 1, fontSize: '12px' }}
              >
                View
              </PrimaryButton>
            )}
          </Box>
        </Stack>
      </Box>

      <ClipShareDialog
        open={shareOpen}
        onClose={() => setShareOpen(false)}
        clipId={liveClip.id!}
      />

      {/* Player dialog */}
      <Dialog open={viewerOpen} onClose={() => setViewerOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', py: 1 }}>
          <Typography sx={{ fontWeight: 600, fontSize: '14px' }}>{liveClip.name}</Typography>
          <IconButton size="small" onClick={() => setViewerOpen(false)}>
            <CloseIcon fontSize="small" />
          </IconButton>
        </DialogTitle>
        <DialogContent sx={{ p: 0, bgcolor: '#000' }}>
          {videoUrl && (
            <video
              controls
              autoPlay
              src={videoUrl}
              style={{ width: '100%', display: 'block', maxHeight: '70vh' }}
            />
          )}
        </DialogContent>
      </Dialog>
    </>
  );
});
