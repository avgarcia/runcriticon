import { HttpErrorResponse } from '@angular/common/http';
import { ErrorResponse } from '../../api/generated/models/error-response';

/**
 * Catálogo `code` → mensaje localizado (ADR-0012 D19). Sincronizado a mano con los códigos que
 * emite el backend: el `ErrorMapper.kt` de cada módulo (hay uno por bounded context, porque cada
 * uno tiene su propia sealed class de errores), más `SessionController.kt` y
 * `GlobalRestExceptionHandler.kt`.
 * El frontend nunca muestra `message` del backend directamente al usuario.
 *
 * Además del `code` plano, admite claves compuestas `"CODE:field:message"` o `"CODE:message"`
 * (mensajes de error específicos por motivo dentro de INVALID_INPUT) para distinguir motivos que
 * hoy colapsan en el mismo `code` genérico (p. ej. varios
 * `INVALID_INPUT`). `message` se usa aquí solo como **discriminante** para elegir la clave —
 * nunca se interpola ni se muestra tal cual; ver `messageForError`.
 */
export const ERROR_MESSAGES: Record<string, string> = {
  FORBIDDEN: $localize`No tienes permiso para esta acción.`,
  NOT_FOUND: $localize`No se ha encontrado el recurso.`,
  INVALID_INPUT: $localize`Revisa los datos introducidos.`,
  // Motivos genéricos de INVALID_INPUT, sin campo concreto: cubren cualquier formulario
  // que reenvíe el `reason` de dominio `blank`/`too_long` sin necesitar una clave por campo.
  'INVALID_INPUT:blank': $localize`Este campo no puede quedar vacío.`,
  'INVALID_INPUT:too_long': $localize`El valor es demasiado largo.`,
  // Ficha del club (`Club.rename`): mensaje más preciso que el genérico de arriba.
  'INVALID_INPUT:nombre:blank': $localize`El nombre del club no puede quedar vacío.`,
  'INVALID_INPUT:nombre:too_long': $localize`El nombre del club no puede pasar de 200 caracteres.`,
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
  // Aviso de impacto al archivar un tag o valor en uso, ADR-0002 D10: bloqueo de archivado. El diálogo de impacto ya debería haberlo evitado
  // -- este mensaje es la red de seguridad ante una llamada directa a la API o una carrera.
  TAG_KEY_REQUIRED_BY_GROUP: $localize`El tag lo requiere el filtro de un grupo; edítalo antes de archivar.`,
  TAG_VALUE_REQUIRED_BY_GROUP: $localize`El valor lo requiere el filtro de un grupo; edítalo antes de archivar.`,
  DUPLICATE_LABEL: $localize`Ya existe un elemento con ese nombre.`,
  LABEL_BLANK: $localize`Escribe un nombre.`,
  LABEL_TOO_LONG: $localize`El nombre es demasiado largo.`,
  // Tipo de eje y metadata de carrera.
  TAG_KEY_NOT_RACE: $localize`Este eje no admite metadata de carrera.`,
  TAG_KEY_HAS_RACE_VALUES: $localize`Archiva o limpia las carreras del eje antes de convertirlo en simple.`,

  // Clasificación de alumnos.
  STUDENT_NOT_FOUND: $localize`No se ha encontrado el alumno.`,
  TAG_VALUE_NOT_ASSIGNABLE: $localize`Ese valor está archivado y ya no se puede asignar.`,

  // Grupos.
  GROUP_NOT_FOUND: $localize`No se ha encontrado el grupo.`,
  COACH_NOT_FOUND: $localize`No se ha encontrado el entrenador.`,

  // Planificación — sesiones.
  SESSION_NOT_FOUND: $localize`No se ha encontrado la sesión.`,
  DUPLICATE_SESSION_DAY: $localize`Ya hay una sesión ese día.`,
  DAY_OUTSIDE_WEEK: $localize`El día debe caer dentro de la semana del plan.`,
  REST_WITH_LOAD: $localize`Una sesión de descanso no lleva volumen ni ritmo.`,
  INVALID_VOLUME: $localize`El volumen debe ser mayor que cero.`,
  NOTES_TOO_LONG: $localize`Las notas no pueden pasar de 1000 caracteres.`,
  WEEK_NOT_MONDAY: $localize`La semana debe empezar en lunes.`,

  // Planificación — publicación.
  PLAN_ALREADY_PUBLISHED: $localize`El plan ya está publicado.`,
  PLAN_WITHOUT_SESSIONS: $localize`El plan no tiene ninguna sesión.`,
  PROJECTION_STALE: $localize`La membresía del grupo está desactualizada; inténtalo de nuevo en unos segundos.`,

  // Planificación — personalizaciones.
  PERSONALIZATION_NOT_FOUND: $localize`El alumno no tiene personalización en esa sesión.`,
  STUDENT_NOT_IN_PLAN: $localize`El alumno no pertenece a este plan.`,

  // Seguimiento — reporte de sesión.
  NO_SESSION_THAT_DAY: $localize`No hay ninguna sesión programada ese día.`,
  FUTURE_DAY: $localize`No se puede reportar un día futuro.`,
  VALORACION_REQUERIDA: $localize`Indica cómo te has sentido.`,
  MOTIVO_REQUERIDO: $localize`Indica el motivo.`,

  // Consentimiento de datos de salud.
  CONSENTIMIENTO_REQUERIDO: $localize`Debes dar tu consentimiento para el tratamiento de datos de salud.`,
  VERSION_CONSENTIMIENTO_OBSOLETA: $localize`El texto de consentimiento ha cambiado; recárgalo antes de continuar.`,

  // Seguimiento — puerta de consentimiento (segunda entrega del consentimiento explícito RGPD). El componente que atrapa este código añade
  // además un enlace a "Mi cuenta"; este texto es el que ve cualquier otro caller genérico.
  CONSENTIMIENTO_NO_VIGENTE: $localize`Necesitas dar tu consentimiento de datos de salud antes de reportar.`,

  // Seguimiento — marcas del alumno.
  TIEMPO_INVALIDO: $localize`El tiempo debe ser mayor que cero.`,

  // Seguimiento — reajuste de día. NO_SESSION_THAT_DAY se reutiliza del reporte de sesión:
  // misma causa exacta ("no hay sesión ese día").
  DIA_PASADO: $localize`No se puede reajustar un día pasado.`,
  DESTINO_FUERA_DE_RANGO: $localize`El día destino debe estar dentro de los próximos 7 días.`,
  DIA_DESTINO_OCUPADO: $localize`Ese día ya tiene una sesión.`,
};

const FALLBACK_MESSAGE = $localize`No se ha podido completar la operación. Inténtalo de nuevo.`;

function errorBody(err: unknown): ErrorResponse | null {
  return err instanceof HttpErrorResponse ? (err.error as ErrorResponse | null) : null;
}

/**
 * Traduce el `code` del `ErrorResponse` del backend a un mensaje localizado (ADR-0012 D19).
 *
 * Resuelve en cascada de más a menos específico: `code:field:message`, luego
 * `code:message`, luego `code` a secas, y por último el fallback genérico. `message` solo actúa
 * como discriminante de la búsqueda — el texto que sale siempre es del catálogo, nunca el crudo
 * del backend.
 */
export function messageForError(err: unknown): string {
  const body = errorBody(err);
  const code = body?.code;
  if (!code) return FALLBACK_MESSAGE;
  const reason = body?.message;
  const field = body?.field;
  if (field && reason && ERROR_MESSAGES[`${code}:${field}:${reason}`]) {
    return ERROR_MESSAGES[`${code}:${field}:${reason}`];
  }
  if (reason && ERROR_MESSAGES[`${code}:${reason}`]) {
    return ERROR_MESSAGES[`${code}:${reason}`];
  }
  return ERROR_MESSAGES[code] ?? FALLBACK_MESSAGE;
}

/** Campo del formulario que originó el error, si el backend lo indica (`ErrorResponse.field`). */
export function fieldOf(err: unknown): string | null {
  return errorBody(err)?.field ?? null;
}

/** El `code` crudo del backend, para el caller que necesita distinguir un código concreto (p. ej. para
 * añadir un enlace) en vez de conformarse con el mensaje genérico de [messageForError]. */
export function codeOf(err: unknown): string | null {
  return errorBody(err)?.code ?? null;
}
