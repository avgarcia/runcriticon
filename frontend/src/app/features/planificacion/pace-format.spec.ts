import { formatPace, parsePace } from './pace-format';

describe('pace-format', () => {
  it('formatPace convierte segundos/km a m:ss', () => {
    expect(formatPace(225)).toBe('3:45');
    expect(formatPace(60)).toBe('1:00');
    expect(formatPace(5)).toBe('0:05');
  });

  it('parsePace convierte m:ss a segundos/km', () => {
    expect(parsePace('3:45')).toBe(225);
    expect(parsePace('1:00')).toBe(60);
  });

  it('parsePace y formatPace son inversos', () => {
    expect(formatPace(parsePace('4:30')!)).toBe('4:30');
  });

  it('parsePace rechaza un formato invalido', () => {
    expect(parsePace('abc')).toBeNull();
    expect(parsePace('3:75')).toBeNull();
    expect(parsePace('3.45')).toBeNull();
    expect(parsePace('')).toBeNull();
  });
});
