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
