// 'goalkeeper': a person whose kit matched none of the three confirmed colors well enough (the
// worker's rejection class). It carries no team: the goalkeeper's side is not determined.
export type Team = 'home' | 'away' | 'referee' | 'goalkeeper';

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
  // A frontend simítása (player/smoothing.ts) tölti ki, a backend JSON-ja nem tartalmazza: ha ezek megvannak,
  // a `tx`/`ty` már a simított érték, a nyers pedig itt marad (összehasonlításhoz, mérésekhez).
  rawTx?: number;
  rawTy?: number;
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
