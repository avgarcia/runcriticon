package com.runcriticon.seguimiento.domain

/**
 * Heurística MVP para la alerta "ritmo fuera de objetivo" (LAL-116): `reporte_sesion.notas` es texto libre, sin
 * ningún campo numérico de ritmo real conseguido, así que en el MVP se busca lenguaje que sugiera una desviación
 * — un análisis real contra FIT/GPX queda fuera del MVP (`docs/wireframes/08-coach-alerts.md`, regla "Ritmo
 * fuera de objetivo"). Falso negativo si el alumno no usa estas palabras; falso positivo si las usa sin
 * relación con el ritmo — limitación conocida y documentada, no un bug.
 *
 * Función pura, testeable en aislamiento: [com.runcriticon.seguimiento.application.ports.outbound.persistence.CoachAlertReader]
 * trae las notas candidatas de la BD, esta función decide cuáles disparan la alerta.
 */
fun matchesPaceOffTargetHeuristic(notes: String): Boolean {
    val normalized = notes.lowercase()
    return PACE_OFF_TARGET_PHRASES.any { normalized.contains(it) }
}

private val PACE_OFF_TARGET_PHRASES =
    listOf(
        "por encima",
        "más rápido",
        "mas rapido",
        "más lento",
        "mas lento",
    )
