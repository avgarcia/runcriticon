package com.runcriticon.seguimiento.domain

/**
 * Ritmo ya resuelto para el alumno, tal como lo pinta la vista "hoy" (spec 06). El alumno nunca ve la
 * distinción absoluto/relativo como tal — solo el texto sutil ("basado en tu 10K") que delata
 * [Relative.reference] cuando el ritmo viene de una marca — pero el origen sí se persiste (LAL-32
 * necesita filtrar por él para recalcular tras una marca nueva).
 */
sealed interface ResolvedPace {
    /** Origen `ABSOLUTO`: el valor tal cual escribió el entrenador, igual para todo el grupo. */
    data class Absolute(
        val secondsPerKm: Int,
    ) : ResolvedPace

    /**
     * Origen `RELATIVO`: `marca + delta` cuando el alumno tiene la marca de [reference] — ver
     * [resolveRelativePace]. Si no la tiene, [secondsPerKm] es `null` y el alumno ve el empty state con CTA
     * "Añade tu marca de [reference]" (AC3).
     *
     * Invariante: si [secondsPerKm] no es `null`, [deltaSecondsPerKm] tampoco lo es — un ritmo resuelto
     * siempre sabe de qué delta salió. La única forma de romperlo sería construir esta clase a mano con
     * datos inconsistentes; `resolveRelativePace` es el único punto que la construye en el camino de
     * escritura.
     *
     * [deltaSecondsPerKm] es `null` únicamente al releer de la BD una fila proyectada **antes** de LAL-32,
     * cuya columna no tenía backfill posible (ver la migración). Esa fila queda varada en "sin resolver"
     * hasta que se vuelva a publicar el plan que la originó — no hay delta que recuperar.
     */
    data class Relative(
        val reference: RaceDistance,
        val deltaSecondsPerKm: Int?,
        val secondsPerKm: Int? = null,
    ) : ResolvedPace {
        init {
            require(secondsPerKm == null || deltaSecondsPerKm != null) {
                "Relative resuelto (secondsPerKm != null) sin deltaSecondsPerKm: estado inconsistente"
            }
        }
    }
}

/**
 * Resuelve un ritmo `Relativo` contra la marca real del alumno (ADR-0002 D8): `marca.paceSecondsPerKm() +
 * delta`, con suelo de 1 s/km — un delta absurdo no puede producir un ritmo negativo o cero que viole el
 * CHECK de la BD y tumbe un listener del outbox (que no tiene reintentos con backoff, ADR-0007).
 *
 * [mark] `null` ⇒ el alumno todavía no tiene la marca de [reference]: ritmo sin resolver.
 */
fun resolveRelativePace(
    reference: RaceDistance,
    deltaSecondsPerKm: Int,
    mark: StudentMark?,
): ResolvedPace.Relative =
    ResolvedPace.Relative(
        reference = reference,
        deltaSecondsPerKm = deltaSecondsPerKm,
        secondsPerKm = mark?.let { maxOf(1, it.paceSecondsPerKm() + deltaSecondsPerKm) },
    )
