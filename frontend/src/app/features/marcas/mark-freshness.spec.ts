import { fixClock, restoreClock } from '../../../testing/clock';
import { freshnessLabel, isStale, monthsSince } from './mark-freshness';

describe('mark-freshness', () => {
  // 2026-08-17 a mediodía; los meses se cuentan como días/30, sin calendario.
  beforeEach(() => fixClock(new Date('2026-08-17T12:00:00Z')));
  afterEach(() => restoreClock());

  describe('monthsSince', () => {
    it('cuenta 0 dentro del mismo mes', () => {
      expect(monthsSince('2026-08-01T12:00:00Z')).toBe(0);
    });

    it('cuenta por bloques de 30 días', () => {
      expect(monthsSince('2026-07-18T12:00:00Z')).toBe(1);
      expect(monthsSince('2026-02-20T12:00:00Z')).toBe(5);
    });
  });

  describe('isStale', () => {
    it('una marca de hace 5 meses sigue fresca', () => {
      expect(isStale('2026-03-20T12:00:00Z')).toBe(false);
    });

    it('a partir de 6 meses (180 días) se considera vieja', () => {
      expect(isStale('2026-02-17T12:00:00Z')).toBe(true);
    });

    it('el día 179 aún no, el 180 sí: el umbral es exacto', () => {
      expect(isStale('2026-02-19T12:00:00Z')).toBe(false);
      expect(isStale('2026-02-18T12:00:00Z')).toBe(true);
    });
  });

  describe('freshnessLabel', () => {
    it('«este mes» si hace menos de 30 días', () => {
      expect(freshnessLabel('2026-08-10T12:00:00Z')).toBe('Actualizada este mes');
    });

    it('singular con un mes', () => {
      expect(freshnessLabel('2026-07-10T12:00:00Z')).toBe('Actualizada hace 1 mes');
    });

    it('plural con varios meses', () => {
      expect(freshnessLabel('2026-02-17T12:00:00Z')).toBe('Actualizada hace 6 meses');
    });
  });
});
