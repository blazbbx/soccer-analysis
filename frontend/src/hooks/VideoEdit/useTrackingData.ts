import { useEffect, useRef } from 'react';
import type { TrackingDataResponse, TrackingEntry } from '../../types/trackingData';
import type { PlacedLabel } from '../../context/VideoPlayerContext';
import { LABEL_ITEMS, TRACKING_LABEL_KEY_MAP } from '../../constants/labels';
import { useVideoPlayer } from '../../context/VideoPlayerContext';

export type TrackingFrameMap = Map<number, TrackingEntry[]>;

export const useTrackingData = (trackingDataUrl: string | undefined): TrackingFrameMap => {
  const { initLabels } = useVideoPlayer();
  const frameMapRef = useRef<TrackingFrameMap>(new Map());
  const fetchedUrlRef = useRef<string | undefined>(undefined);

  useEffect(() => {
    if (!trackingDataUrl || fetchedUrlRef.current === trackingDataUrl) return;

    fetchedUrlRef.current = trackingDataUrl;

    fetch(trackingDataUrl)
      .then((res) => res.json())
      .then((data: TrackingDataResponse) => {
        const frameMap: TrackingFrameMap = new Map();
        for (const entry of data.trackingData) {
          const bucket = frameMap.get(entry.frame) ?? [];
          bucket.push(entry);
          frameMap.set(entry.frame, bucket);
        }
        frameMapRef.current = frameMap;

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
      })
      .catch((err) => {
        console.error('Failed to load tracking data:', err);
      });
  }, [trackingDataUrl, initLabels]);

  return frameMapRef.current;
};
