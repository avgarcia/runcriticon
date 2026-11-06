/**
 * Fija solo `Date` (no los temporizadores) para que un spec no dependa del día en que se ejecuta: las fechas
 * "relativas a hoy" cruzan la medianoche o cambian de semana según el reloj de quien lanza los tests. Los
 * temporizadores siguen reales porque Angular y RxJS los necesitan para resolver `fixture.whenStable()`.
 *
 * Llamar a [restoreClock] en `afterEach`.
 */
export function fixClock(now: Date): void {
  jest.useFakeTimers({
    now,
    doNotFake: [
      'setTimeout',
      'clearTimeout',
      'setInterval',
      'clearInterval',
      'setImmediate',
      'clearImmediate',
      'nextTick',
      'queueMicrotask',
      'requestAnimationFrame',
      'cancelAnimationFrame',
      'requestIdleCallback',
      'cancelIdleCallback',
      'hrtime',
      'performance',
    ],
  });
}

export function restoreClock(): void {
  jest.useRealTimers();
}
