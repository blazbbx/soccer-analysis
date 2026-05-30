import React, { useState } from 'react';
import { Box, CircularProgress, Stack, Typography } from '@mui/material';
import ContentCutIcon from '@mui/icons-material/ContentCut';
import { useTranslation } from 'react-i18next';
import { useGetClips } from '../../../../api/generated/match-controller/match-controller';
import type { ClipResponse } from '../../../../api/generated/model';
import { ClipCard } from './ClipCard';
import { useClipDelete } from '../../../../services/recordingService';

interface ClipsSidebarProps {
  matchId: string;
  isEditor: boolean;
}

export const ClipsSidebar = React.memo(({ matchId, isEditor }: ClipsSidebarProps) => {
  const { t } = useTranslation();
  const { data, isLoading } = useGetClips(matchId);
  const clips = (data as unknown as ClipResponse[]) ?? [];
  const { deleteClip } = useClipDelete(matchId);
  const [deletingClipId, setDeletingClipId] = useState<string | null>(null);

  const handleDelete = async (clipId: string) => {
    setDeletingClipId(clipId);
    try {
      await deleteClip(clipId);
    } finally {
      setDeletingClipId(null);
    }
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 }}>
      <Box
        sx={{
          p: 2,
          borderBottom: '1px solid',
          borderColor: 'divider',
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          flexShrink: 0,
        }}
      >
        <ContentCutIcon sx={{ color: '#8b5cf6', fontSize: '18px', transform: 'rotate(270deg)' }} />
        <Typography
          sx={{ color: 'text.secondary', fontWeight: 'bold', fontSize: '14px', textTransform: 'uppercase' }}
        >
          {t('editor.clips')} {!isLoading && `(${clips.length})`}
        </Typography>
      </Box>

      <Box sx={{ flex: 1, overflowY: 'auto', p: 2 }}>
        {isLoading ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', pt: 4 }}>
            <CircularProgress size={28} />
          </Box>
        ) : clips.length === 0 ? (
          <Typography sx={{ color: 'text.secondary', fontSize: '13px', fontStyle: 'italic', textAlign: 'center' }}>
            {t('editor.no-clips')}
          </Typography>
        ) : (
          <Stack spacing={2}>
            {clips.map((clip) => (
              <ClipCard
                key={clip.id}
                clip={clip}
                onDelete={clip.id ? () => handleDelete(clip.id!) : undefined}
                isDeleting={deletingClipId === clip.id}
                isEditor={isEditor}
              />
            ))}
          </Stack>
        )}
      </Box>
    </Box>
  );
});
