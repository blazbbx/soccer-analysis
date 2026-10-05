// Pálya-méretek méterben (kispálya-arányok a 105x68-as nézethez).
export const PITCH_W = 105;
export const PITCH_H = 68;

export const PENALTY_DEPTH = 16.5;
export const PENALTY_WIDTH = 40.32;
export const GOAL_DEPTH = 5.5;
export const GOAL_WIDTH = 18.32;
export const GOAL_POST_DEPTH = 2.44;
export const GOAL_POST_WIDTH = 7.32;
export const CENTER_R = 9.15;
export const PENALTY_SPOT = 11;
export const CORNER_R = 1;

export const PLAYER_RADIUS = 1.6;
export const BALL_RADIUS = 0.9;

// A látható terület a pályán túl a kapukat és némi margót is tartalmaz.
export const PITCH_VIEW = { x: -3, y: -2, w: PITCH_W + 6, h: PITCH_H + 4 } as const;

// A 2D nézet belső (logikai) felbontása. Fix, hogy a megjelenítés és a felvétel ugyanazt a képet
// használja, és a rajzok normalizált koordinátái ne függjenek a képernyő méretétől.
export const PX_PER_METER = 12;
export const PITCH_CANVAS = {
  width: PITCH_VIEW.w * PX_PER_METER,
  height: PITCH_VIEW.h * PX_PER_METER,
} as const;
