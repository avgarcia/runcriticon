import { HttpErrorResponse } from '@angular/common/http';
import { ErrorResponse } from '../../api/generated/models/error-response';

/**
 * Catálogo `code` → mensaje localizado (ADR-0012 D19). Sincronizado a mano con los códigos que
 * emite el backend (`ErrorMapper.kt`, `SessionController.kt`, `GlobalRestExceptionHandler.kt`).
 * El frontend nunca muestra `message` del backend directamente al usuario.
 */
export const ERROR_MESSAGES: Record<string, string> = {
  FORBIDDEN: 'No tienes permiso para esta acción.',
  NOT_FOUND: 'No se ha encontrado el recurso.',
  INVALID_INPUT: 'Revisa los datos introducidos.',
  CONFLICT: 'La operación no se puede completar por un conflicto con el estado actual.',
  RATE_LIMITED: 'Demasiados intentos. Espera unos segundos.',
  UNAUTHORIZED: 'No se ha podido autenticar.',
  PASSWORD_EXPIRED: 'Tu contraseña ha caducado; crea una nueva para continuar.',
  INTERNAL_ERROR: 'Algo ha ido mal. Vuelve a intentarlo.',
  METHOD_NOT_ALLOWED: 'Operación no soportada.',
  UNSUPPORTED_MEDIA_TYPE: 'Formato de petición no soportado.',
};

const FALLBACK_MESSAGE = 'No se ha podido completar la operación. Inténtalo de nuevo.';

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
