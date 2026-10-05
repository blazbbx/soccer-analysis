import { useEffect, useRef, useState } from 'react';
import type { TrackingDataResponse } from '../../../../types/trackingData';
import type { PlacedLabel } from '../../../../context/LabelsContext';
import { LABEL_ITEMS, TRACKING_LABEL_KEY_MAP } from '../../../../constants/labels';
import { useLabels } from '../../../../context/LabelsContext';
import { useFrameSnapshotSource } from '../../../../player/hooks';
import { createTrackingStore } from '../../../../player/trackingStore';

export interface TrackingDataResult {
  videoFps: number;
  isLoaded: boolean;
}

// Betölti a tracking JSON-t: az adatot a pillanatkép-forrásba teszi (ebből olvasnak a nézetek), az
// eseményeket pedig címkékké alakítja.
export const useTrackingData = (trackingDataUrl: string | undefined): TrackingDataResult => {
  const { initLabels } = useLabels();
  const snapshots = useFrameSnapshotSource();
  const [videoFps, setVideoFps] = useState<number>(0);
  const [isLoaded, setIsLoaded] = useState<boolean>(!trackingDataUrl);
  const fetchedUrlRef = useRef<string | undefined>(undefined);

  useEffect(() => {
    if (!trackingDataUrl || fetchedUrlRef.current === trackingDataUrl) return;

    fetchedUrlRef.current = trackingDataUrl;

    fetch(trackingDataUrl)
      .then((res) => res.json())
      .then((data: TrackingDataResponse) => {
        snapshots.setTracking(createTrackingStore(data));
        setVideoFps(data.videoFps);

        const mergedLabelData = Object.assign({}, ...data.labelData);
        const placedLabels: PlacedLabel[] = [];
        let idCounter = 0;

        for (const [key, hotkey] of Object.entries(TRACKING_LABEL_KEY_MAP)) {
          const frames = mergedLabelData[key] as number[] | undefined;
          if (!frames) continue;

          const config = LABEL_ITEMS.find((item) => item.hotkey === hotkey);
          if (!config) continue;

          for (const frame of frames) {
            placedLabels.push({
              id: `tracking-${idCounter++}`,
              config,
              time: frame / data.videoFps,
            });
          }
        }

        placedLabels.sort((a, b) => a.time - b.time);
        initLabels(placedLabels);
        setIsLoaded(true);
      })
      .catch((err) => {
        console.error('Failed to load tracking data:', err);
        setIsLoaded(true);
      });
  }, [trackingDataUrl, initLabels, snapshots]);

  return { videoFps, isLoaded };
};
