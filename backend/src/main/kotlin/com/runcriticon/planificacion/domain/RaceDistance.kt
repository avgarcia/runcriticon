package com.runcriticon.planificacion.domain

/**
 * Distancia de referencia de un ritmo relativo (ADR-0002 D6). Los identificadores Kotlin no pueden empezar por
 * dígito; el puente a los valores persistidos (`5K`, `10K`, `21K`, `42K`, columna `ritmo_ref_distancia`) vive en
 * el mapeador de persistencia, no aquí.
 */
enum class RaceDistance {
    FIVE_K,
    TEN_K,
    HALF_MARATHON,
    MARATHON,
}
