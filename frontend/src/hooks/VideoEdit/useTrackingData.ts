import { useEffect, useRef, useState } from 'react';
import type { TrackingDataResponse, TrackingEntry } from '../../types/trackingData';
import type { PlacedLabel } from '../../context/VideoPlayerContext';
import { LABEL_ITEMS, TRACKING_LABEL_KEY_MAP } from '../../constants/labels';
import { useVideoPlayer } from '../../context/VideoPlayerContext';

export type TrackingFrameMap = Map<number, TrackingEntry[]>;

export interface TrackingDataResult {
  frameMap: TrackingFrameMap;
  videoFps: number;
}

export const useTrackingData = (trackingDataUrl: string | undefined): TrackingDataResult => {
  const { initLabels } = useVideoPlayer();
  const [frameMap, setFrameMap] = useState<TrackingFrameMap>(new Map());
  const [videoFps, setVideoFps] = useState<number>(0);
  const fetchedUrlRef = useRef<string | undefined>(undefined);

  useEffect(() => {
    if (!trackingDataUrl || fetchedUrlRef.current === trackingDataUrl) return;

    fetchedUrlRef.current = trackingDataUrl;

    fetch(trackingDataUrl)
      .then((res) => res.json())
      .then((data: TrackingDataResponse) => {
        const newFrameMap: TrackingFrameMap = new Map();
        for (const entry of data.trackingData) {
          const bucket = newFrameMap.get(entry.frame) ?? [];
          bucket.push(entry);
          newFrameMap.set(entry.frame, bucket);
        }
        setFrameMap(newFrameMap);
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
      })
      .catch((err) => {
        console.error('Failed to load tracking data:', err);
      });
  }, [trackingDataUrl, initLabels]);

  return { frameMap, videoFps };
};
