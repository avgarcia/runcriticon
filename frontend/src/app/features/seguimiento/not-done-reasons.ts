/** Los 7 motivos de "no hecho" del glosario §Seguimiento, mismo patrón que `SESSION_TYPES` de
 * planificación. `MOLESTIAS` activa la marca de dolor en el backend — no es un valor cualquiera. */
export type NotDoneReason = 'CANSANCIO' | 'TRABAJO' | 'VIAJE' | 'ENFERMEDAD' | 'SIN_TIEMPO' | 'MOLESTIAS' | 'OTRA';

export const NOT_DONE_REASONS: { value: NotDoneReason; label: string }[] = [
  { value: 'CANSANCIO', label: $localize`Cansancio` },
  { value: 'TRABAJO', label: $localize`Trabajo` },
  { value: 'VIAJE', label: $localize`Viaje` },
  { value: 'ENFERMEDAD', label: $localize`Enfermedad` },
  { value: 'SIN_TIEMPO', label: $localize`Sin tiempo` },
  { value: 'MOLESTIAS', label: $localize`Molestias` },
  { value: 'OTRA', label: $localize`Otra` },
];
