/**
 * Formateo de fechas en castellano sin depender de `DatePipe`/locale de Angular: el proyecto no
 * registra `registerLocaleData` (MVP castellano único, ADR-0012 D9) y `DatePipe` sin ella cae a
 * nombres de día/mes en inglés.
 */

const WEEKDAYS_LONG = [
  'domingo',
  'lunes',
  'martes',
  'miércoles',
  'jueves',
  'viernes',
  'sábado',
];

const MONTHS_LONG = [
  'enero',
  'febrero',
  'marzo',
  'abril',
  'mayo',
  'junio',
  'julio',
  'agosto',
  'septiembre',
  'octubre',
  'noviembre',
  'diciembre',
];

const WEEKDAYS_SHORT = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];

/** Parsea una fecha `YYYY-MM-DD` como fecha local (evita el desfase de `new Date(iso)`, que la
 * interpreta en UTC). */
function parseIsoDateLocal(iso: string): Date {
  const [year, month, day] = iso.split('-').map(Number);
  return new Date(year, month - 1, day);
}

/** `"jueves 28 de agosto"` — saludo largo de la maqueta `vista-hoy-alumno.html`. */
export function formatLongDateEs(iso: string): string {
  const date = parseIsoDateLocal(iso);
  return `${WEEKDAYS_LONG[date.getDay()]} ${date.getDate()} de ${MONTHS_LONG[date.getMonth()]}`;
}

/** `"Jue"` — abreviatura de 3 letras para la tira de días de la semana. */
export function formatWeekdayShortEs(iso: string): string {
  return WEEKDAYS_SHORT[parseIsoDateLocal(iso).getDay()];
}

/** La fecha de hoy en `YYYY-MM-DD`, en la zona local del navegador. */
export function todayIsoDate(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  const day = String(now.getDate()).padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

/** `"hace 5 min"` / `"hace 3 h"` / `"hace 2 d"` — banner de "editando reporte enviado hace X" del
 * spec 07 (estado 3, edición). Redondea hacia abajo; menos de un minuto es "hace un momento". */
export function formatRelativeShortEs(iso: string): string {
  const elapsedMs = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(elapsedMs / 60_000);
  if (minutes < 1) return 'hace un momento';
  if (minutes < 60) return `hace ${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `hace ${hours} h`;
  const days = Math.floor(hours / 24);
  return `hace ${days} d`;
}
