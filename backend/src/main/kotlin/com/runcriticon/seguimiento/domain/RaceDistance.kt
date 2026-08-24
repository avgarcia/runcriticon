package com.runcriticon.seguimiento.domain

/**
 * Distancia de referencia de un ritmo relativo (ADR-0002 D6). Mismo catálogo que
 * `planificacion.domain.RaceDistance`; el puente a los literales persistidos/del evento (`5K`, `10K`, `21K`,
 * `42K`) vive en el mapeador de infraestructura, no aquí — los identificadores Kotlin no pueden empezar por
 * dígito.
 */
enum class RaceDistance {
    FIVE_K,
    TEN_K,
    HALF_MARATHON,
    MARATHON,
}
