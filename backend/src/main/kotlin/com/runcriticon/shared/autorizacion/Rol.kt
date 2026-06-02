package com.runcriticon.shared.autorizacion

/**
 * Rol del principal. Un único rol por usuario en MVP (ADR-0003 D2). El multi-rol queda
 * fuera del MVP (ADR-0015): si aparece, se reabre ADR-0003 D2.
 */
sealed class Rol {
    data object Admin : Rol()

    data object Entrenador : Rol()

    data object Alumno : Rol()

    /** Código estable del rol (persistencia, authorities de Spring Security, DTOs). */
    val codigo: String
        get() =
            when (this) {
                Admin -> "ADMIN"
                Entrenador -> "ENTRENADOR"
                Alumno -> "ALUMNO"
            }
}
