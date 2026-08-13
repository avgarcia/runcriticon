package com.runcriticon.planificacion.domain

/**
 * Estado de un `WeeklyPlan`. Los valores son los que se persisten en la columna `estado` de `plan_semanal`.
 */
enum class PlanStatus {
    BORRADOR,
    PUBLICADO,
}
