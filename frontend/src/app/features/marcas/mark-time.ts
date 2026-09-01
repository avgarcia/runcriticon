/** Partes de un tiempo, para los tres `time-box` del modal (h/min/seg). */
export interface MarkTimeParts {
  hours: number;
  minutes: number;
  seconds: number;
}

const SECONDS_PER_HOUR = 3600;
const SECONDS_PER_MINUTE = 60;

/** Descompone un total de segundos en horas/minutos/segundos. */
export function secondsToParts(totalSeconds: number): MarkTimeParts {
  const hours = Math.floor(totalSeconds / SECONDS_PER_HOUR);
  const minutes = Math.floor((totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE);
  const seconds = totalSeconds % SECONDS_PER_MINUTE;
  return { hours, minutes, seconds };
}

/** Recompone el total de segundos a partir de las tres cajas del modal. */
export function partsToSeconds(parts: MarkTimeParts): number {
  return parts.hours * SECONDS_PER_HOUR + parts.minutes * SECONDS_PER_MINUTE + parts.seconds;
}

/** Formato de la card: `mm:ss` si no llega a la hora (fiel al wireframe, ej. `22:45`), `h:mm:ss` si la
 * supera (21K/42K reales suelen hacerlo — el wireframe no cubre este caso, ver decisión del plan). */
export function formatMarkTime(totalSeconds: number): string {
  const { hours, minutes, seconds } = secondsToParts(totalSeconds);
  const pad = (value: number) => value.toString().padStart(2, '0');
  if (hours > 0) {
    return `${hours}:${pad(minutes)}:${pad(seconds)}`;
  }
  return `${pad(minutes)}:${pad(seconds)}`;
}
