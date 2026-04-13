export interface TrackingEntry {
  frame: number;
  player_id: number;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

export interface LabelData {
  goal?: number[];
  corner?: number[];
  freekick?: number[];
  highlight?: number[];
}

export interface TrackingDataResponse {
  videoFps: number;
  trackingData: TrackingEntry[];
  labelData: LabelData[];
}
