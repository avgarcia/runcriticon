/** Umbral de "marca vieja" del wireframe `mis-marcas.html`: 3 meses ya se pinta fresca, 8 meses ya
 * lleva el aviso — 6 es el punto intermedio razonable entre ambos ejemplos. No es un dato normativo
 * del ADR ni del glosario, a confirmar con negocio si se quiere afinar. */
const STALE_THRESHOLD_MONTHS = 6;
const MS_PER_DAY = 86_400_000;
const DAYS_PER_MONTH = 30;

/** Meses transcurridos desde `iso` (redondeo por días/30, suficiente para un aviso aproximado — no se
 * necesita precisión calendario para "hace N meses"). */
export function monthsSince(iso: string): number {
  const elapsedMs = Date.now() - new Date(iso).getTime();
  const days = Math.floor(elapsedMs / MS_PER_DAY);
  return Math.floor(days / DAYS_PER_MONTH);
}

/** `true` si la marca lleva sin actualizarse más del umbral (wireframe: "quizá ya la has mejorado"). */
export function isStale(iso: string): boolean {
  return monthsSince(iso) >= STALE_THRESHOLD_MONTHS;
}

/** `"Actualizada hace 3 meses"` — texto de meta de la card, singular si es el mes en curso. */
export function freshnessLabel(iso: string): string {
  const months = monthsSince(iso);
  if (months < 1) return $localize`Actualizada este mes`;
  if (months === 1) return $localize`Actualizada hace 1 mes`;
  return $localize`Actualizada hace ${months}:months: meses`;
}
