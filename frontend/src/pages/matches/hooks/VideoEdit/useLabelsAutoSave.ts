import { useEffect, useRef } from 'react';
import { useVideoPlayer } from '../../../../context/VideoPlayerContext';
import { useUpdateTrackingLabelData } from '../../../../api/generated/match-controller/match-controller';
import { TRACKING_LABEL_KEY_MAP } from '../../../../constants/labels';
import type { PlacedLabel } from '../../../../context/VideoPlayerContext';
import type { LabelData } from '../../../../types/trackingData';

const REVERSE_KEY_MAP: Record<string, string> = Object.fromEntries(
  Object.entries(TRACKING_LABEL_KEY_MAP).map(([key, hotkey]) => [hotkey, key])
);

function toFrameLabelData(labels: PlacedLabel[], videoFps: number): LabelData {
  const result: LabelData = {};
  for (const label of labels) {
    const key = REVERSE_KEY_MAP[label.config.hotkey];
    if (!key) continue;
    (result[key as keyof LabelData] ??= []).push(Math.round(label.time * videoFps));
  }
  return result;
}

export const useLabelsAutoSave = (
  matchId: string,
  videoFps: number,
  isLoaded: boolean,
): { isSaving: boolean } => {
  const { labels } = useVideoPlayer();
  const { mutate, isPending } = useUpdateTrackingLabelData();
  const isLoadedRef = useRef(false);

  useEffect(() => {
    const wasLoaded = isLoadedRef.current;
    isLoadedRef.current = isLoaded;

    if (!isLoaded) return;
    if (!wasLoaded) return; // initialization render — skip

    mutate({
      id: matchId,
      data: { labelData: toFrameLabelData(labels, videoFps) as Record<string, unknown> },
    });
  }, [labels, isLoaded]); // eslint-disable-line react-hooks/exhaustive-deps

  return { isSaving: isPending };
};
