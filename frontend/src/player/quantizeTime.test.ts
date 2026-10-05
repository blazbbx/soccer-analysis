import { describe, expect, it } from 'vitest';
import { quantizeTime } from './quantizeTime';

describe('quantizeTime', () => {
  it('0 felbontásnál változatlanul átadja az időt', () => {
    expect(quantizeTime(12.3456, 0)).toBe(12.3456);
  });

  it('lefelé kerekít a lépcsőre', () => {
    expect(quantizeTime(12.9, 1)).toBe(12);
    expect(quantizeTime(1.3, 0.25)).toBe(1.25);
  });

  it('a lépcsőn belül ugyanazt az értéket adja', () => {
    expect(quantizeTime(3.01, 0.25)).toBe(quantizeTime(3.24, 0.25));
  });
});
