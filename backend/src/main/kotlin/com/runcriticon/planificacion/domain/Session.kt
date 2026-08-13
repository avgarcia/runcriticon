package com.runcriticon.planificacion.domain

import java.time.LocalDate

/**
 * Una sesión de entrenamiento del plan. Sin más campos que el día y el ritmo: el editor de sesión (LAL-24) no
 * existe todavía — esta entidad hija solo tiene que existir para que el agregado cargue completo (ADR-0008 D17).
 */
data class Session(
    val id: SessionId,
    val day: LocalDate,
    val pace: Pace? = null,
)
