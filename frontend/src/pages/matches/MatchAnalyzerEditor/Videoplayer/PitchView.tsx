import { forwardRef, useEffect, useImperativeHandle, useRef } from 'react';
import { Box } from '@mui/material';
import { AnnotationLayer, type AnnotationLayerHandle } from '../../../../annotation/AnnotationLayer';
import { renderPitchActors, renderPitchBackground, type TeamColors } from '../../../../annotation/pitchScene';
import { PITCH_CANVAS } from '../../../../constants/pitch';
import { useFrameSnapshotSource } from '../../../../player/hooks';
import type { RecordingSurfaceHandle } from './recordingSurface';

interface PitchViewProps {
  teamColors?: TeamColors;
}

// A 2D nézet: a scene (pálya, játékosok) és az Annotation réteg ugyanazon a fix felbontáson él,
// ezt olvassa közvetlenül a felvétel is.
export const PitchView = forwardRef<RecordingSurfaceHandle, PitchViewProps>(({ teamColors }, ref) => {
  const snapshots = useFrameSnapshotSource();
  const sceneRef = useRef<HTMLCanvasElement>(null);
  const layerRef = useRef<AnnotationLayerHandle>(null);

  useImperativeHandle(
    ref,
    () => ({
      get sceneCanvas() { return sceneRef.current; },
      get contentCanvas() { return layerRef.current?.contentCanvas ?? null; },
    }),
    [],
  );

  useEffect(() => {
    const ctx = sceneRef.current?.getContext('2d');
    if (!ctx) return;

    const background = document.createElement('canvas');
    background.width = PITCH_CANVAS.width;
    background.height = PITCH_CANVAS.height;
    const backgroundCtx = background.getContext('2d');
    if (!backgroundCtx) return;
    renderPitchBackground(backgroundCtx);

    const draw = () => {
      ctx.drawImage(background, 0, 0);
      renderPitchActors(ctx, snapshots.getSnapshot(), teamColors);
    };
    draw();
    return snapshots.subscribe(draw);
  }, [snapshots, teamColors]);

  return (
    <Box
      sx={{
        position: 'relative',
        width: '100%',
        aspectRatio: `${PITCH_CANVAS.width} / ${PITCH_CANVAS.height}`,
        bgcolor: '#1a6b1a',
        overflow: 'hidden',
      }}
    >
      <canvas
        ref={sceneRef}
        width={PITCH_CANVAS.width}
        height={PITCH_CANVAS.height}
        style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%' }}
      />
      <AnnotationLayer ref={layerRef} view="pitch" />
    </Box>
  );
});
