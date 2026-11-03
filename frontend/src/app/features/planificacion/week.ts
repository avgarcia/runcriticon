/**
 * El lunes de la semana de [reference], como `YYYY-MM-DD` en hora local.
 *
 * Se formatea con los componentes de fecha locales, no con `toISOString()`: esta convierte a UTC y, entre las 00:00 y
 * el desfase horario de la zona (01:00–02:00 en España), devuelve el día anterior; el backend rechaza entonces la
 * fecha porque ya no es un lunes. Al revés pasaría al oeste de Greenwich por la noche.
 */
export function mondayOfWeek(reference: Date = new Date()): string {
  // `getDay()` es 0 (domingo) a 6 (sábado). El desplazamiento da 0 para el lunes y 6 para el domingo.
  const daysSinceMonday = (reference.getDay() + 6) % 7;
  // Medianoche local del lunes: `Date` normaliza un día fuera de rango, así que cruza mes, año y cambios de hora.
  const monday = new Date(
    reference.getFullYear(),
    reference.getMonth(),
    reference.getDate() - daysSinceMonday,
  );
  const month = String(monday.getMonth() + 1).padStart(2, '0');
  const day = String(monday.getDate()).padStart(2, '0');
  return `${monday.getFullYear()}-${month}-${day}`;
}
