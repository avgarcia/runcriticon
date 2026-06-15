package com.runcriticon.shared.autorizacion.modelo

/**
 * Rol del principal. Un único rol por usuario en MVP (ADR-0003 D2). El multi-rol queda
 * fuera del MVP (ADR-0015): si aparece, se reabre ADR-0003 D2.
 *
 * Es un `enum` (no sealed class) por alineación con ADR-0003 D2 y porque así es Serializable de
 * forma nativa (preserva el singleton al deserializar la sesión de Spring Session).
 */
enum class Rol {
    ADMIN,
    ENTRENADOR,
    ALUMNO,
    ;

    /** Código estable del rol (persistencia, authorities de Spring Security, DTOs). */
    val codigo: String get() = name
}
