package com.runcriticon.planificacion.domain

/**
 * Tipo de sesión de entrenamiento. Catálogo cerrado del MVP, literales de `docs/glosario.md` §Planificación —
 * los valores son los que se persisten en la columna `tipo` de `sesion`.
 */
enum class SessionType {
    RODAJE,
    SERIES,
    TEMPO,
    TIRADA_LARGA,
    FARTLEK,
    CUESTAS,
    PROGRESIVO,
    FUERZA_CROSS,
    COMPETICION,
    DESCANSO,
}
