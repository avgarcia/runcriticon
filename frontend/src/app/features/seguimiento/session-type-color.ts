import { MiResolvedSessionResponse } from '../../api/generated/models/mi-resolved-session-response';

export type ResolvedSessionType = MiResolvedSessionResponse['tipo'];

/** Clase Tailwind de fondo por tipo de sesión — tokens `--color-t-*` de `styles.css`, tomados de
 * docs/diseno/vista-hoy-alumno.html (LAL-29). */
const COLOR_CLASS_BY_TYPE: Record<ResolvedSessionType, string> = {
  RODAJE: 'bg-t-rodaje',
  SERIES: 'bg-t-series',
  TEMPO: 'bg-t-tempo',
  TIRADA_LARGA: 'bg-t-larga',
  FARTLEK: 'bg-t-fartlek',
  CUESTAS: 'bg-t-cuestas',
  PROGRESIVO: 'bg-t-progresivo',
  FUERZA_CROSS: 'bg-t-fuerza',
  COMPETICION: 'bg-t-competicion',
  DESCANSO: 'bg-t-descanso',
};

/** Icono por tipo de sesión (docs/wireframes/06-student-today.md: "Icono + tipo de sesión"). */
const ICON_BY_TYPE: Record<ResolvedSessionType, string> = {
  RODAJE: '🏃',
  SERIES: '🔥',
  TEMPO: '⏱',
  TIRADA_LARGA: '🏔',
  FARTLEK: '⚡',
  CUESTAS: '⛰',
  PROGRESIVO: '📈',
  FUERZA_CROSS: '💪',
  COMPETICION: '🎯',
  DESCANSO: '💤',
};

export function sessionTypeColorClass(type: ResolvedSessionType): string {
  return COLOR_CLASS_BY_TYPE[type];
}

export function sessionTypeIcon(type: ResolvedSessionType): string {
  return ICON_BY_TYPE[type];
}
