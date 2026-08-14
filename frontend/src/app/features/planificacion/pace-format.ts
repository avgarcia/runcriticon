/** Convierte segundos/km a `m:ss` para pintar el ritmo (p.ej. 225 -> "3:45"). Usado tanto por el
 * formulario del editor (parseo inverso, ver {@link parsePace}) como por la tarjeta de la rejilla. */
export function formatPace(secondsPerKm: number): string {
  const minutes = Math.floor(secondsPerKm / 60);
  const seconds = secondsPerKm % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

/** Convierte `m:ss` a segundos/km. `null` si no tiene ese formato exacto (segundos 0-59). */
export function parsePace(value: string): number | null {
  const match = /^(\d{1,3}):([0-5]\d)$/.exec(value.trim());
  if (!match) return null;
  return Number(match[1]) * 60 + Number(match[2]);
}
