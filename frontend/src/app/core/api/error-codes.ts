import { HttpErrorResponse } from '@angular/common/http';
import { ErrorResponse } from '../../api/generated/models/error-response';

/**
 * Catálogo `code` → mensaje localizado (ADR-0012 D19). Sincronizado a mano con los códigos que
 * emite el backend: el `ErrorMapper.kt` de cada módulo (hay uno por bounded context, porque cada
 * uno tiene su propia sealed class de errores), más `SessionController.kt` y
 * `GlobalRestExceptionHandler.kt`.
 * El frontend nunca muestra `message` del backend directamente al usuario.
 */
export const ERROR_MESSAGES: Record<string, string> = {
  FORBIDDEN: $localize`No tienes permiso para esta acción.`,
  NOT_FOUND: $localize`No se ha encontrado el recurso.`,
  INVALID_INPUT: $localize`Revisa los datos introducidos.`,
  CONFLICT: $localize`La operación no se puede completar por un conflicto con el estado actual.`,
  RATE_LIMITED: $localize`Demasiados intentos. Espera unos segundos.`,
  UNAUTHORIZED: $localize`No se ha podido autenticar.`,
  PASSWORD_EXPIRED: $localize`Tu contraseña ha caducado; crea una nueva para continuar.`,
  INTERNAL_ERROR: $localize`Algo ha ido mal. Vuelve a intentarlo.`,
  METHOD_NOT_ALLOWED: $localize`Operación no soportada.`,
  UNSUPPORTED_MEDIA_TYPE: $localize`Formato de petición no soportado.`,

  // Taxonomía del club. `LABEL_TOO_LONG` va genérico a propósito: el límite difiere entre eje (40)
  // y valor (60), y este catálogo no acepta parámetros — el límite concreto lo comunica el
  // `maxlength` del input.
  TAG_KEY_NOT_FOUND: $localize`No se ha encontrado el tag.`,
  TAG_VALUE_NOT_FOUND: $localize`No se ha encontrado el valor.`,
  TAG_KEY_ARCHIVED: $localize`El tag está archivado; reactívalo antes de añadirle valores.`,
  DUPLICATE_LABEL: $localize`Ya existe un elemento con ese nombre.`,
  LABEL_BLANK: $localize`Escribe un nombre.`,
  LABEL_TOO_LONG: $localize`El nombre es demasiado largo.`,

  // Clasificación de alumnos.
  STUDENT_NOT_FOUND: $localize`No se ha encontrado el alumno.`,
  TAG_VALUE_NOT_ASSIGNABLE: $localize`Ese valor está archivado y ya no se puede asignar.`,

  // Grupos.
  GROUP_NOT_FOUND: $localize`No se ha encontrado el grupo.`,
  COACH_NOT_FOUND: $localize`No se ha encontrado el entrenador.`,

  // Planificación — sesiones (LAL-24).
  SESSION_NOT_FOUND: $localize`No se ha encontrado la sesión.`,
  DUPLICATE_SESSION_DAY: $localize`Ya hay una sesión ese día.`,
  DAY_OUTSIDE_WEEK: $localize`El día debe caer dentro de la semana del plan.`,
  REST_WITH_LOAD: $localize`Una sesión de descanso no lleva volumen ni ritmo.`,
  INVALID_VOLUME: $localize`El volumen debe ser mayor que cero.`,
  NOTES_TOO_LONG: $localize`Las notas no pueden pasar de 1000 caracteres.`,
  WEEK_NOT_MONDAY: $localize`La semana debe empezar en lunes.`,

  // Planificación — publicación (LAL-25).
  PLAN_ALREADY_PUBLISHED: $localize`El plan ya está publicado.`,
  PLAN_WITHOUT_SESSIONS: $localize`El plan no tiene ninguna sesión.`,
  PROJECTION_STALE: $localize`La membresía del grupo está desactualizada; inténtalo de nuevo en unos segundos.`,
};

const FALLBACK_MESSAGE = $localize`No se ha podido completar la operación. Inténtalo de nuevo.`;

function errorBody(err: unknown): ErrorResponse | null {
  return err instanceof HttpErrorResponse ? (err.error as ErrorResponse | null) : null;
}

/** Traduce el `code` del `ErrorResponse` del backend a un mensaje localizado (ADR-0012 D19). */
export function messageForError(err: unknown): string {
  const code = errorBody(err)?.code;
  return (code && ERROR_MESSAGES[code]) || FALLBACK_MESSAGE;
}

/** Campo del formulario que originó el error, si el backend lo indica (`ErrorResponse.field`). */
export function fieldOf(err: unknown): string | null {
  return errorBody(err)?.field ?? null;
}
