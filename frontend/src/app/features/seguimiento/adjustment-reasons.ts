/** Los motivos del reajuste de día, mismo patrón que `NOT_DONE_REASONS` del reporte de
 * sesión — enum propio, no una extensión de `NotDoneReason`: son conceptos distintos ("por qué no
 * hiciste la sesión" frente a "por qué reajustas el día"). `MOLESTIAS` activa la marca de dolor en
 * el backend, igual que en el reporte.
 *
 * `LESION` (LAL-131) no es una píldora más de {@link ADJUSTMENT_REASONS}: tiene su propia tarjeta
 * ("Avisar de lesión") en `RescheduleDialogComponent`, que fija `accion` a `SALTADA` y dispara el
 * modal de confirmación del cambio de `estado` — el backend rechaza `LESION` con `accion` `MOVIDA`. */
export type AdjustmentReason = 'CANSANCIO' | 'MOLESTIAS' | 'IMPREVISTO' | 'LESION';

export const ADJUSTMENT_REASONS: { value: AdjustmentReason; label: string }[] = [
  { value: 'CANSANCIO', label: $localize`Cansancio` },
  { value: 'MOLESTIAS', label: $localize`Molestias` },
  { value: 'IMPREVISTO', label: $localize`Imprevisto` },
];
