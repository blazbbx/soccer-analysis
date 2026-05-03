import { Alert, Box, Chip, CircularProgress, Stack, Typography } from '@mui/material';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  useGetClipById,
  useGetRenderedClipDownloadUrl,
} from '../../api/generated/clip-controller/clip-controller';
import type { ClipResponse } from '../../api/generated/model';

const TERMINAL_STATUSES = new Set(['COMPLETED', 'FAILED']);

const STATUS_COLORS: Record<string, 'success' | 'info' | 'warning' | 'error' | 'default'> = {
  COMPLETED: 'success',
  PROCESSING: 'info',
  QUEUED: 'warning',
  PENDING_UPLOAD: 'default',
  FAILED: 'error',
};

export const ClipViewer = () => {
  const { clipId } = useParams<{ clipId: string }>();
  const { t } = useTranslation();

  const { data, isLoading, isError } = useGetClipById(clipId!, {
    query: {
      enabled: !!clipId,
      refetchInterval: (query) => {
        const status = (query.state.data as unknown as ClipResponse)?.renderStatus;
        return TERMINAL_STATUSES.has(status ?? '') ? false : 3000;
      },
    },
  });

  const clip = data as unknown as ClipResponse | undefined;
  const isCompleted = clip?.renderStatus === 'COMPLETED';

  const { data: urlData } = useGetRenderedClipDownloadUrl(clipId!, {
    query: { enabled: isCompleted && !!clipId },
  });

  const videoUrl: string | undefined = urlData
    ? Object.values(urlData as unknown as Record<string, string>)[0]
    : undefined;

  return (
    <Box sx={{ p: 3, maxWidth: 900, mx: 'auto' }}>
      {isLoading && (
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <CircularProgress size={24} />
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            {t('clips.viewer.loading')}
          </Typography>
        </Box>
      )}

      {isError && (
        <Alert severity="error">{t('clips.viewer.not-found')}</Alert>
      )}

      {clip && (
        <Stack spacing={2}>
          <Box>
            <Typography variant="body2" sx={{ color: 'text.secondary', mb: 0.5 }}>
              {clip.matchDisplayName}
            </Typography>
            <Typography variant="h5" sx={{ fontWeight: 600 }}>
              {clip.name}
            </Typography>
          </Box>

          {!isCompleted && (
            <Stack spacing={1}>
              <Chip
                label={clip.renderStatus ?? 'UNKNOWN'}
                color={STATUS_COLORS[clip.renderStatus ?? ''] ?? 'default'}
                sx={{ alignSelf: 'flex-start' }}
              />
              <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                {t('clips.viewer.processing')}
              </Typography>
            </Stack>
          )}

          {isCompleted && videoUrl && (
            <Box
              component="video"
              controls
              src={videoUrl}
              sx={{
                width: '100%',
                borderRadius: 2,
                display: 'block',
                bgcolor: '#000',
                maxHeight: '70vh',
              }}
            />
          )}

          {isCompleted && !videoUrl && (
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <CircularProgress size={20} />
              <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                {t('clips.viewer.loading')}
              </Typography>
            </Box>
          )}
        </Stack>
      )}
    </Box>
  );
};
