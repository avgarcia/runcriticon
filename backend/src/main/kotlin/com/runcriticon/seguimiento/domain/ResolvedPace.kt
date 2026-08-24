package com.runcriticon.seguimiento.domain

/**
 * Ritmo ya resuelto para el alumno, tal como lo pinta la vista "hoy" (spec 06): un único valor absoluto,
 * nunca el origen absoluto/relativo — el alumno no ve esa distinción, salvo el texto sutil que delata
 * [referenceDistance] cuando el ritmo viene de una marca.
 *
 * Exactamente uno de [secondsPerKm] y [missingMark] está presente, o ninguno de los dos si la sesión no tiene
 * ritmo (`PublishedSession.ritmoTipo == null`, p. ej. `DESCANSO`):
 *  - Origen `ABSOLUTO` → [secondsPerKm] relleno, [referenceDistance] y [missingMark] a `null`.
 *  - Origen `RELATIVO` con marca del alumno → [secondsPerKm] calculado, [referenceDistance] con el contexto
 *    ("basado en tu 10K"). **No implementado todavía**: las marcas del alumno llegan con LAL-31; hasta entonces
 *    todo `RELATIVO` cae en el caso siguiente.
 *  - Origen `RELATIVO` sin marca → todos a `null` salvo [missingMark], que dispara el empty state ("Añade tu
 *    marca de [distancia]").
 */
data class ResolvedPace(
    val secondsPerKm: Int? = null,
    val referenceDistance: RaceDistance? = null,
    val missingMark: RaceDistance? = null,
)
