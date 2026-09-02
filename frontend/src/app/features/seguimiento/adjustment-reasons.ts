/** Los 3 motivos del reajuste de día (LAL-33), mismo patrón que `NOT_DONE_REASONS` del reporte de
 * sesión — enum propio, no una extensión de `NotDoneReason`: son conceptos distintos ("por qué no
 * hiciste la sesión" frente a "por qué reajustas el día"). `MOLESTIAS` activa la marca de dolor en
 * el backend, igual que en el reporte. */
export type AdjustmentReason = 'CANSANCIO' | 'MOLESTIAS' | 'IMPREVISTO';

export const ADJUSTMENT_REASONS: { value: AdjustmentReason; label: string }[] = [
  { value: 'CANSANCIO', label: $localize`Cansancio` },
  { value: 'MOLESTIAS', label: $localize`Molestias` },
  { value: 'IMPREVISTO', label: $localize`Imprevisto` },
];
