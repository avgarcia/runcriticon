import { Ritmo } from '../../api/generated/models/ritmo';

export type PaceReference = NonNullable<Ritmo['referencia']>;

/** Las cuatro distancias de referencia de un ritmo relativo (LAL-27), mismo orden y valores que
 * `MARK_DISTANCES` en `features/marcas` — la marca que el alumno registra para poder resolverlo. */
export const PACE_REFERENCES: { value: PaceReference; label: string }[] = [
  { value: '5K', label: '5K' },
  { value: '10K', label: '10K' },
  { value: '21K', label: '21K' },
  { value: '42K', label: '42K' },
];
