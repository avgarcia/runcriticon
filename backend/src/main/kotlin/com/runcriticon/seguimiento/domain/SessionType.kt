package com.runcriticon.seguimiento.domain

/**
 * Tipo de sesión de entrenamiento, tal como llega en `PlanPublicado.sesiones[].tipo`. Mismo catálogo cerrado que
 * `planificacion.domain.SessionType` (docs/glosario.md §Planificación) — duplicado a propósito: cada módulo
 * mantiene su propio dominio, sin núcleo compartido entre bounded contexts (ADR-0008).
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
