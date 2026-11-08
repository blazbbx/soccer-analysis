import { Alert, Box, Button, Chip, CircularProgress, IconButton, Stack, Typography } from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import { useTranslation } from 'react-i18next';
import type { GoalDetectionStatus } from '../hooks/useMatchUploadFlow';
import {
  GATE_CORNER_LABEL_KEYS,
  allGatesComplete,
  canAddGate,
  emptyGate,
  estimatedCount,
  nextGateId,
  nextUnplacedIndex,
  removeGate,
  unplacedCount,
  GATE_CORNER_COUNT,
  type GateDraft,
} from './gateDraft';

interface GateListProps {
  gates: GateDraft[];
  onGatesChange: (gates: GateDraft[]) => void;
  placingGateId: number | null;
  onPlacingChange: (gateId: number | null) => void;
  status: GoalDetectionStatus;
  /** The user chose not to wait for a goal detection that is still running. */
  skippedWaiting: boolean;
  onSkipWaiting: () => void;
}

/**
 * The goals under the preview image: what the detection found, what is still missing, and the
 * ways to fix it (drag on the image, place a missing corner, delete a false goal, add a missed one).
 */
export const GateList = ({
  gates,
  onGatesChange,
  placingGateId,
  onPlacingChange,
  status,
  skippedWaiting,
  onSkipWaiting,
}: GateListProps) => {
  const { t } = useTranslation();
  const waiting = status === 'PENDING' && !skippedWaiting;
  const estimated = estimatedCount(gates);

  const addGate = () => {
    const gate = emptyGate(nextGateId(gates));
    onGatesChange([...gates, gate]);
    onPlacingChange(gate.id);
  };

  return (
    <Box sx={{ mt: 2 }}>
      <Typography variant="subtitle2">{t('upload.goals.title')}</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        {t('upload.goals.description')}
      </Typography>

      {waiting && (
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 1 }}>
          <CircularProgress size={18} />
          <Typography variant="body2">{t('upload.goals.searching')}</Typography>
          <Button size="small" onClick={onSkipWaiting}>
            {t('upload.goals.skipWaiting')}
          </Button>
        </Stack>
      )}

      {!waiting && gates.length === 0 && (
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          {status === 'FAILED' ? t('upload.goals.failed') : t('upload.goals.noneFound')}
        </Typography>
      )}

      <Stack spacing={1}>
        {gates.map((gate, index) => {
          const missing = unplacedCount(gate);
          const gateEstimated = gate.corners.filter((c) => c.source === 'estimated').length;
          const isPlacing = placingGateId === gate.id;
          const nextIndex = nextUnplacedIndex(gate);
          return (
            <Stack key={gate.id} direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
              <Typography variant="body2" sx={{ fontWeight: 600, minWidth: 64 }}>
                {t('upload.goals.gate', { n: index + 1 })}
              </Typography>
              <Chip
                size="small"
                label={t('upload.goals.cornersPlaced', { placed: GATE_CORNER_COUNT - missing, total: GATE_CORNER_COUNT })}
              />
              {missing > 0 && (
                <Chip size="small" color="warning" label={t('upload.goals.missing', { count: missing })} />
              )}
              {gateEstimated > 0 && (
                <Chip size="small" variant="outlined" color="warning" label={t('upload.goals.estimated', { count: gateEstimated })} />
              )}
              {missing > 0 && !isPlacing && (
                <Button size="small" variant="outlined" onClick={() => onPlacingChange(gate.id)}>
                  {t('upload.goals.place')}
                </Button>
              )}
              {isPlacing && nextIndex !== null && (
                <>
                  <Typography variant="body2" color="primary">
                    {t('upload.goals.placeHint', { corner: t(GATE_CORNER_LABEL_KEYS[nextIndex]) })}
                  </Typography>
                  <Button size="small" onClick={() => onPlacingChange(null)}>
                    {t('upload.goals.cancelPlace')}
                  </Button>
                </>
              )}
              <IconButton
                size="small"
                aria-label={t('upload.goals.delete')}
                title={t('upload.goals.delete')}
                onClick={() => onGatesChange(removeGate(gates, gate.id))}
              >
                <DeleteOutlineIcon fontSize="small" />
              </IconButton>
            </Stack>
          );
        })}
      </Stack>

      {canAddGate(gates) && placingGateId === null && (
        <Button size="small" startIcon={<AddIcon />} onClick={addGate} sx={{ mt: 1 }}>
          {t('upload.goals.add')}
        </Button>
      )}

      {estimated > 0 && (
        <Alert severity="warning" sx={{ mt: 1 }}>
          {t('upload.goals.estimatedWarning')}
        </Alert>
      )}
      {!allGatesComplete(gates) && (
        <Alert severity="info" sx={{ mt: 1 }}>
          {t('upload.goals.incomplete')}
        </Alert>
      )}
    </Box>
  );
};
