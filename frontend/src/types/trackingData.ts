export type Team = 'home' | 'away' | 'referee';

export interface TrackingEntry {
  frame: number;
  player_id: number;
  team?: Team;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  tx?: number;
  ty?: number;
}

export interface BallEntry {
  frame: number;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  tx?: number;
  ty?: number;
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
  ballData?: BallEntry[];
  labelData: LabelData[];
}
