import { describe, expect, it } from 'vitest';
import {
  MAX_GATES,
  allGatesComplete,
  canAddGate,
  emptyGate,
  estimateMissingCorner,
  estimatedCount,
  gateFromPayload,
  gatesFromPayload,
  isGateComplete,
  nextGateId,
  nextUnplacedIndex,
  removeGate,
  replaceGate,
  toConfirmedGates,
  unplacedCount,
  withCornerAt,
  type GateCorner,
  type GoalPayload,
} from './gateDraft';

const placed = (x: number, y: number): GateCorner => ({ x, y, source: 'detected' });
const unplaced = (): GateCorner => ({ x: null, y: null, source: 'needs_manual_review' });

// A goal that is exactly a parallelogram: P1 (100,50) P2 (200,50) P3 (110,150) P4 (210,150).
const exact = [placed(100, 50), placed(200, 50), placed(110, 150), placed(210, 150)];

const payload = (...corners: ({ x: number; y: number; source?: string } | null)[]): GoalPayload => ({
  corners: corners.map((c) =>
    c ? { x: c.x, y: c.y, source: c.source ?? 'detected' } : { x: null, y: null, source: 'needs_manual_review' },
  ),
});

describe('estimateMissingCorner', () => {
  it('egy pontos paralelogrammánál mind a négy sarkot visszaadja', () => {
    for (let missing = 0; missing < 4; missing += 1) {
      const corners = exact.map((c, i) => (i === missing ? unplaced() : c));
      expect(estimateMissingCorner(corners, missing)).toEqual({ x: exact[missing].x, y: exact[missing].y });
    }
  });

  it('egész pixelre kerekít', () => {
    const corners = [placed(100.4, 50.4), placed(200.4, 50.4), placed(110.4, 150.4), unplaced()];
    expect(estimateMissingCorner(corners, 3)).toEqual({ x: 210, y: 150 });
  });

  it('nem becsül, ha a másik három közül bármelyik hiányzik', () => {
    const corners = [placed(100, 50), unplaced(), placed(110, 150), unplaced()];
    expect(estimateMissingCorner(corners, 3)).toBeNull();
  });
});

describe('gateFromPayload', () => {
  it('négy meglévő sarkot változatlanul átvesz, becslés nélkül', () => {
    const gate = gateFromPayload(
      payload({ x: 100, y: 50 }, { x: 200, y: 50 }, { x: 110, y: 150, source: 'clamped' }, { x: 210, y: 150 }),
      1,
    );
    expect(gate.corners.map((c) => c.source)).toEqual(['detected', 'detected', 'clamped', 'detected']);
    expect(estimatedCount([gate])).toBe(0);
    expect(isGateComplete(gate)).toBe(true);
  });

  it('pontosan egy hiányzó sarkot becsült pozícióval pótol, becsültként jelölve', () => {
    const gate = gateFromPayload(payload({ x: 100, y: 50 }, { x: 200, y: 50 }, { x: 110, y: 150 }, null), 1);
    expect(gate.corners[3]).toEqual({ x: 210, y: 150, source: 'estimated' });
    expect(gate.corners.slice(0, 3).every((c) => c.source === 'detected')).toBe(true);
    expect(estimatedCount([gate])).toBe(1);
  });

  it('két hiányzó sarokra nem talál ki pozíciót', () => {
    const gate = gateFromPayload(payload({ x: 100, y: 50 }, { x: 200, y: 50 }, null, null), 1);
    expect(unplacedCount(gate)).toBe(2);
    expect(gate.corners[2]).toEqual({ x: null, y: null, source: 'needs_manual_review' });
    expect(estimatedCount([gate])).toBe(0);
    expect(nextUnplacedIndex(gate)).toBe(2);
  });

  it('átlós hiányzó párra sem becsül', () => {
    const gate = gateFromPayload(payload(null, { x: 200, y: 50 }, { x: 110, y: 150 }, null), 1);
    expect(unplacedCount(gate)).toBe(2);
  });

  it('a hiányzó x/y mezőt (undefined) és a null-t egyaránt hiányzó pozíciónak veszi', () => {
    const gate = gateFromPayload(
      { corners: [{ x: 1, y: 2 }, { x: 3, y: 4 }, { x: 5, y: 6 }, { source: 'needs_manual_review' }] },
      1,
    );
    expect(gate.corners[3].source).toBe('estimated');
  });

  it('kevesebb mint négy sarkot hiányzó sarkokkal egészít ki, ismeretlen forrást detected-nek vesz', () => {
    const gate = gateFromPayload({ corners: [{ x: 1, y: 2, source: 'valami' }] }, 7);
    expect(gate.id).toBe(7);
    expect(gate.corners).toHaveLength(4);
    expect(gate.corners[0].source).toBe('detected');
    expect(unplacedCount(gate)).toBe(3);
  });

  it('a pozíció nélküli sarok forrása mindig needs_manual_review, akkor is, ha mást állít', () => {
    const gate = gateFromPayload({ corners: [{ x: null, y: null, source: 'detected' }] }, 1);
    expect(gate.corners[0].source).toBe('needs_manual_review');
  });
});

describe('gatesFromPayload', () => {
  it('legfeljebb két kaput vesz át, 1-től számozva', () => {
    const goal = payload({ x: 1, y: 1 }, { x: 2, y: 1 }, { x: 1, y: 2 }, { x: 2, y: 2 });
    const gates = gatesFromPayload([goal, goal, goal]);
    expect(gates.map((g) => g.id)).toEqual([1, 2]);
    expect(gates).toHaveLength(MAX_GATES);
  });

  it('null/undefined bemenetből üres lista lesz', () => {
    expect(gatesFromPayload(null)).toEqual([]);
    expect(gatesFromPayload(undefined)).toEqual([]);
  });
});

describe('szerkesztés', () => {
  it('a mozgatott vagy kijelölt sarok manual lesz, a többi változatlan, az eredeti nem módosul', () => {
    const gate = gateFromPayload(payload({ x: 100, y: 50 }, { x: 200, y: 50 }, { x: 110, y: 150 }, null), 1);
    const moved = withCornerAt(gate, 3, 215.4, 148.6);
    expect(moved.corners[3]).toEqual({ x: 215, y: 149, source: 'manual' });
    expect(moved.corners[0]).toBe(gate.corners[0]);
    expect(gate.corners[3].source).toBe('estimated');
    expect(estimatedCount([moved])).toBe(0);
  });

  it('az üres kapu négy kattintásra teljes, sorrendben P1..P4', () => {
    let gate = emptyGate(3);
    expect(nextUnplacedIndex(gate)).toBe(0);
    for (let i = 0; i < 4; i += 1) {
      expect(nextUnplacedIndex(gate)).toBe(i);
      gate = withCornerAt(gate, i, 10 * i, 5 * i);
    }
    expect(nextUnplacedIndex(gate)).toBeNull();
    expect(isGateComplete(gate)).toBe(true);
  });

  it('törlés, csere, új azonosító és a kapuszám korlátja', () => {
    const a = emptyGate(1);
    const b = emptyGate(2);
    expect(nextGateId([a, b])).toBe(3);
    expect(nextGateId([])).toBe(1);
    expect(canAddGate([a])).toBe(true);
    expect(canAddGate([a, b])).toBe(false);
    expect(removeGate([a, b], 1)).toEqual([b]);
    const filled = withCornerAt(a, 0, 1, 1);
    expect(replaceGate([a, b], filled)).toEqual([filled, b]);
  });
});

describe('mehet-e a küldés', () => {
  it('kapu nélkül igen (hamis detektálás törölve, vagy nincs kapu a képen)', () => {
    expect(allGatesComplete([])).toBe(true);
  });

  it('hiányzó sarkú kapuval nem, a pótlás után igen', () => {
    const open = gateFromPayload(payload({ x: 100, y: 50 }, { x: 200, y: 50 }, null, null), 1);
    expect(allGatesComplete([open])).toBe(false);
    const done = withCornerAt(withCornerAt(open, 2, 110, 150), 3, 210, 150);
    expect(allGatesComplete([done])).toBe(true);
  });

  it('a megerősítés kerek egész pixeleket ad, hiányos kapura hibát dob', () => {
    const gate = gateFromPayload(payload({ x: 100, y: 50 }, { x: 200, y: 50 }, { x: 110, y: 150 }, null), 1);
    expect(toConfirmedGates([gate])).toEqual([
      {
        corners: [
          { x: 100, y: 50 },
          { x: 200, y: 50 },
          { x: 110, y: 150 },
          { x: 210, y: 150 },
        ],
      },
    ]);
    expect(() => toConfirmedGates([emptyGate(1)])).toThrow();
    expect(toConfirmedGates([])).toEqual([]);
  });
});
