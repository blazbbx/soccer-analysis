/**
 * The goals on the upload confirmation screen, as a pure model (no React, no DOM).
 *
 * A goal has four corners in the order the goal model was trained with:
 *   P1 top-left, P2 top-right, P3 bottom-left, P4 bottom-right.
 * This is NOT the order of the field corners (top-left, top-right, bottom-right, bottom-left), and the
 * outline of a goal visits the corners as P1, P2, P4, P3 (GATE_OUTLINE_ORDER).
 *
 * Coordinates are pixels of the defished preview frame, the same frame the field corners sit on
 * (both Python workers defish with the same coefficients, so one image serves both).
 *
 * What the worker may hand over for a corner:
 *   - a position (`detected`, or `clamped` to the frame edge), or
 *   - no position (`needs_manual_review`): the model was not confident enough in that keypoint.
 * The rule for the second case:
 *   - exactly one corner missing: the other three fix an estimate, shown as `estimated` and dragged by the user;
 *   - two or more missing: nothing is guessed, the user clicks the corner onto the image.
 */

export const GATE_CORNER_COUNT = 4;
/** A pitch has two goals. */
export const MAX_GATES = 2;
/** Order in which the outline of a goal visits its corners: P1, P2, P4, P3. */
export const GATE_OUTLINE_ORDER = [0, 1, 3, 2] as const;

/** Translation keys of the goal's corner names, in the goal's own order P1..P4 (not the field's). */
export const GATE_CORNER_LABEL_KEYS = [
  'upload.corners.topLeft',
  'upload.corners.topRight',
  'upload.corners.bottomLeft',
  'upload.corners.bottomRight',
] as const;

export type GateCornerSource =
  | 'detected'
  | 'clamped'
  | 'needs_manual_review'
  | 'estimated'
  | 'manual'
  | 'confirmed';

const KNOWN_SOURCES: readonly string[] = [
  'detected',
  'clamped',
  'needs_manual_review',
  'estimated',
  'manual',
  'confirmed',
];

export interface GateCorner {
  x: number | null;
  y: number | null;
  source: GateCornerSource;
}

export interface GateDraft {
  /** Stable key for React and for the "which goal is being placed" state. */
  id: number;
  /** Always GATE_CORNER_COUNT entries, in the order P1..P4. */
  corners: GateCorner[];
}

/** A goal as the server sends it (the worker's output or the stored match record). */
export interface GoalPayload {
  corners?: { x?: number | null; y?: number | null; source?: string | null }[] | null;
}

/** What the confirm endpoint takes: four placed corners per goal. */
export interface ConfirmedGatePayload {
  corners: { x: number; y: number }[];
}

export const isPlaced = (corner: GateCorner): boolean => corner.x !== null && corner.y !== null;

// For a missing corner k: the two corners next to it along the frame, and the opposite one.
const ADJACENT: Record<number, [number, number]> = { 0: [1, 2], 1: [0, 3], 2: [0, 3], 3: [1, 2] };
const OPPOSITE: Record<number, number> = { 0: 3, 1: 2, 2: 1, 3: 0 };

/**
 * Estimates corner `missing` from the other three as the fourth vertex of a parallelogram
 * (P4 = P2 + P3 - P1, and likewise for the others). Returns null unless the other three are placed.
 *
 * Measured on the 82 annotated goals of the training set (with a script that is not part of this repository):
 * the error is a median of 0.33 % of the image diagonal, 7 % of the goal's own diagonal (max 31 %). A
 * mirror-symmetric trapezoid was tried as well and was worse (max 196 % of the goal diagonal), so this
 * is the estimate used. The three known corners were hand-annotated there; with the model's own corners
 * the real error is larger.
 */
export const estimateMissingCorner = (
  corners: GateCorner[],
  missing: number,
): { x: number; y: number } | null => {
  const [a, b] = ADJACENT[missing];
  const opposite = OPPOSITE[missing];
  const needed = [corners[a], corners[b], corners[opposite]];
  if (needed.some((c) => !c || !isPlaced(c))) return null;
  const [ca, cb, co] = needed;
  return {
    x: Math.round((ca.x as number) + (cb.x as number) - (co.x as number)),
    y: Math.round((ca.y as number) + (cb.y as number) - (co.y as number)),
  };
};

const toNumberOrNull = (value: number | null | undefined): number | null =>
  typeof value === 'number' && Number.isFinite(value) ? value : null;

/** Turns one goal from the server into a draft, applying the estimate rule above. */
export const gateFromPayload = (goal: GoalPayload, id: number): GateDraft => {
  const corners: GateCorner[] = [];
  for (let i = 0; i < GATE_CORNER_COUNT; i += 1) {
    const raw = goal.corners?.[i];
    const x = toNumberOrNull(raw?.x);
    const y = toNumberOrNull(raw?.y);
    const placed = x !== null && y !== null;
    const source: GateCornerSource = !placed
      ? 'needs_manual_review'
      : KNOWN_SOURCES.includes(raw?.source ?? '') && raw?.source !== 'needs_manual_review'
        ? (raw?.source as GateCornerSource)
        : 'detected';
    corners.push({ x: placed ? x : null, y: placed ? y : null, source });
  }

  const missing = corners.map((c, i) => (isPlaced(c) ? -1 : i)).filter((i) => i >= 0);
  if (missing.length === 1) {
    const estimate = estimateMissingCorner(corners, missing[0]);
    if (estimate) corners[missing[0]] = { ...estimate, source: 'estimated' };
  }
  return { id, corners };
};

/** At most MAX_GATES goals; ids 1, 2, ... in the order the worker listed them (left to right). */
export const gatesFromPayload = (goals: GoalPayload[] | null | undefined): GateDraft[] =>
  (goals ?? []).slice(0, MAX_GATES).map((goal, i) => gateFromPayload(goal, i + 1));

export const emptyGate = (id: number): GateDraft => ({
  id,
  corners: Array.from({ length: GATE_CORNER_COUNT }, () => ({
    x: null,
    y: null,
    source: 'needs_manual_review' as const,
  })),
});

export const nextGateId = (gates: GateDraft[]): number =>
  gates.reduce((max, g) => Math.max(max, g.id), 0) + 1;

export const canAddGate = (gates: GateDraft[]): boolean => gates.length < MAX_GATES;

/** Index of the first corner without a position, or null when the goal is complete. */
export const nextUnplacedIndex = (gate: GateDraft): number | null => {
  const index = gate.corners.findIndex((c) => !isPlaced(c));
  return index === -1 ? null : index;
};

export const unplacedCount = (gate: GateDraft): number =>
  gate.corners.filter((c) => !isPlaced(c)).length;

export const isGateComplete = (gate: GateDraft): boolean => unplacedCount(gate) === 0;

/** No goals at all is a valid state (the pitch has none in view, or the user deleted a false detection). */
export const allGatesComplete = (gates: GateDraft[]): boolean => gates.every(isGateComplete);

/** Corners still holding an untouched estimate: shown so they are not saved by accident. */
export const estimatedCount = (gates: GateDraft[]): number =>
  gates.reduce((sum, g) => sum + g.corners.filter((c) => c.source === 'estimated').length, 0);

/** The user placed or moved a corner: it is now the user's value (`manual`). Returns a new goal. */
export const withCornerAt = (gate: GateDraft, index: number, x: number, y: number): GateDraft => ({
  ...gate,
  corners: gate.corners.map((c, i) =>
    i === index ? { x: Math.round(x), y: Math.round(y), source: 'manual' as const } : c,
  ),
});

export const replaceGate = (gates: GateDraft[], gate: GateDraft): GateDraft[] =>
  gates.map((g) => (g.id === gate.id ? gate : g));

export const removeGate = (gates: GateDraft[], id: number): GateDraft[] =>
  gates.filter((g) => g.id !== id);

/** The request body part for the confirm endpoint; throws if a goal is still incomplete. */
export const toConfirmedGates = (gates: GateDraft[]): ConfirmedGatePayload[] =>
  gates.map((gate) => {
    if (!isGateComplete(gate)) {
      throw new Error(`Goal ${gate.id} still has corners without a position.`);
    }
    return {
      corners: gate.corners.map((c) => ({ x: Math.round(c.x as number), y: Math.round(c.y as number) })),
    };
  });
