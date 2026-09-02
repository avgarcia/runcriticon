package com.runcriticon.seguimiento.domain

/**
 * Distancia de referencia de un ritmo relativo (ADR-0002 D6). Mismo catálogo que
 * `planificacion.domain.RaceDistance`; el puente a los literales persistidos/del evento (`5K`, `10K`, `21K`,
 * `42K`) vive en el mapeador de infraestructura, no aquí — los identificadores Kotlin no pueden empezar por
 * dígito.
 *
 * [meters] son las distancias oficiales (21,0975 km / 42,195 km redondeadas a metros enteros para 21K/42K),
 * no divisiones triviales de 5K — LAL-32 las usa para convertir el tiempo de una marca a segundos por
 * kilómetro.
 */
enum class RaceDistance(
    val meters: Int,
) {
    FIVE_K(5_000),
    TEN_K(10_000),
    HALF_MARATHON(21_097),
    MARATHON(42_195),
}
