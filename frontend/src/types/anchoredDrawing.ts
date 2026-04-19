export type DrawingTool = 'pen' | 'arrow' | 'circle';

export interface NormalizedPoint {
  x: number; // canvasX / canvas.width
  y: number; // canvasY / canvas.height
}

export interface PlayerRelativePoint {
  dx: number; // (canvasX - playerCenterX) / canvas.width
  dy: number; // (canvasY - playerCenterY) / canvas.height
}

interface BaseDrawing {
  id: string;
  tool: DrawingTool;
  color: string;
}

export interface StaticDrawing extends BaseDrawing {
  type: 'static';
  points: NormalizedPoint[];
}

export interface AnchoredDrawing extends BaseDrawing {
  type: 'anchored';
  playerId: number;
  anchorFrame: number;
  points: PlayerRelativePoint[];
}

export type ClipDrawing = StaticDrawing | AnchoredDrawing;

export const TRACKING_GAP_TOLERANCE = 30;
