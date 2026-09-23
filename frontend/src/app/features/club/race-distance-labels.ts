import { TagValueMetadataRequest } from '../../api/generated/models/tag-value-metadata-request';

export type RaceDistance = NonNullable<TagValueMetadataRequest['distancia']>;

/** Las cuatro distancias del catálogo de carreras, mismo orden y valores que
 * `MARK_DISTANCES` en `features/marcas` — el enum es el mismo, cada contexto lo repite (ADR-0002). */
export const RACE_DISTANCES: { value: RaceDistance; label: string }[] = [
  { value: '5K', label: '5K' },
  { value: '10K', label: '10K' },
  { value: '21K', label: $localize`21K (media maratón)` },
  { value: '42K', label: $localize`42K (maratón)` },
];
