import { TrainingSessionResponse } from '../../api/generated/models/training-session-response';

export type SessionType = TrainingSessionResponse['tipo'];

/** Catálogo cerrado del glosario (docs/glosario.md §Planificación), mismo orden que el editor de sesión. */
export const SESSION_TYPES: { value: SessionType; label: string }[] = [
  { value: 'RODAJE', label: $localize`Rodaje` },
  { value: 'SERIES', label: $localize`Series` },
  { value: 'TEMPO', label: $localize`Tempo` },
  { value: 'TIRADA_LARGA', label: $localize`Tirada larga` },
  { value: 'FARTLEK', label: $localize`Fartlek` },
  { value: 'CUESTAS', label: $localize`Cuestas` },
  { value: 'PROGRESIVO', label: $localize`Progresivo` },
  { value: 'FUERZA_CROSS', label: $localize`Fuerza / Cross` },
  { value: 'COMPETICION', label: $localize`Competición` },
  { value: 'DESCANSO', label: $localize`Descanso` },
];

const LABELS_BY_TYPE = new Map(SESSION_TYPES.map((t) => [t.value, t.label]));

export function sessionTypeLabel(type: SessionType): string {
  return LABELS_BY_TYPE.get(type) ?? type;
}
