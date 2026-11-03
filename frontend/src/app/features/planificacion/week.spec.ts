import { mondayOfWeek } from './week';

describe('mondayOfWeek', () => {
  it('los tests corren en Europe/Madrid: si no, no reproducirían el fallo de la conversión a UTC', () => {
    // 00:30 locales del 19 de agosto son las 22:30 UTC del 18: el día UTC es distinto del local.
    expect(new Date(2026, 7, 19, 0, 30).toISOString()).toBe('2026-08-18T22:30:00.000Z');
  });

  it('de madrugada devuelve el lunes, no el domingo que daría la conversión a UTC', () => {
    // Miércoles 00:30 locales: `toISOString()` del lunes calculado daba 2026-08-16, un domingo que el backend rechaza.
    expect(mondayOfWeek(new Date(2026, 7, 19, 0, 30))).toBe('2026-08-17');
  });

  it('un lunes de madrugada devuelve ese mismo lunes', () => {
    expect(mondayOfWeek(new Date(2026, 7, 17, 0, 30))).toBe('2026-08-17');
  });

  it('un lunes a mediodía devuelve ese mismo lunes', () => {
    expect(mondayOfWeek(new Date(2026, 7, 17, 12, 0))).toBe('2026-08-17');
  });

  it('el domingo pertenece a la semana que acaba, no a la siguiente', () => {
    expect(mondayOfWeek(new Date(2026, 7, 23, 23, 30))).toBe('2026-08-17');
  });

  it('cruza el fin de mes', () => {
    expect(mondayOfWeek(new Date(2026, 8, 5, 10, 0))).toBe('2026-08-31');
  });

  it('cruza el fin de año', () => {
    expect(mondayOfWeek(new Date(2027, 0, 1, 10, 0))).toBe('2026-12-28');
  });

  it('cruza el cambio de hora de marzo', () => {
    // El 29 de marzo de 2026 se adelanta el reloj: la semana del lunes 23 tiene un día de 23 horas.
    expect(mondayOfWeek(new Date(2026, 2, 31, 0, 30))).toBe('2026-03-30');
    expect(mondayOfWeek(new Date(2026, 2, 29, 10, 0))).toBe('2026-03-23');
  });

  it('cruza el cambio de hora de octubre', () => {
    // El 25 de octubre de 2026 se atrasa el reloj: la semana del lunes 19 tiene un día de 25 horas.
    expect(mondayOfWeek(new Date(2026, 9, 26, 0, 30))).toBe('2026-10-26');
    expect(mondayOfWeek(new Date(2026, 9, 25, 10, 0))).toBe('2026-10-19');
  });
});
