import { forwardRef, useImperativeHandle, useRef } from 'react';
import { Box, CircularProgress } from '@mui/material';
import { AnnotationLayer, type AnnotationLayerHandle } from '../../../../annotation/AnnotationLayer';
import { VIDEO_VIEW } from '../../../../constants/video';
import { usePlaybackState } from '../../../../player/hooks';
import { HlsVideo } from './HlsVideo';
import type { RecordingSurfaceHandle } from './recordingSurface';

interface VideoViewProps {
  videoUrl: string;
  isHidden: boolean;
}

// A videónézet: a lejátszó <video> eleme és az Annotation réteg. A videó a nézetváltáskor is megy tovább,
// ezért csak elrejtjük, nem szereljük le.
export const VideoView = forwardRef<RecordingSurfaceHandle, VideoViewProps>(({ videoUrl, isHidden }, ref) => {
  const { isBuffering } = usePlaybackState();
  const layerRef = useRef<AnnotationLayerHandle>(null);

  useImperativeHandle(
    ref,
    () => ({ get contentCanvas() { return layerRef.current?.contentCanvas ?? null; } }),
    [],
  );

  return (
    <Box
      sx={{
        position: 'relative',
        width: '100%',
        aspectRatio: `${VIDEO_VIEW.width} / ${VIDEO_VIEW.height}`,
        bgcolor: '#000',
        overflow: 'hidden',
        display: isHidden ? 'none' : 'block',
      }}
    >
      <HlsVideo videoUrl={videoUrl} />
      <AnnotationLayer ref={layerRef} view="video" />
      {isBuffering && (
        <Box
          sx={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: 'rgba(0,0,0,0.35)',
            zIndex: 10,
            pointerEvents: 'none',
          }}
        >
          <CircularProgress sx={{ color: '#fff' }} size={48} />
        </Box>
      )}
    </Box>
  );
});
