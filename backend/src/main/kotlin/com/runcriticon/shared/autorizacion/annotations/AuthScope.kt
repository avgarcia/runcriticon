package com.runcriticon.shared.autorizacion.annotations

/**
 * Ámbito de filtrado que un método de repositorio aplica sobre sus consultas para no devolver
 * filas fuera del alcance del [Principal] (ADR-0009 D6, defensa anti-IDOR de la guía operativa).
 */
enum class Scope {
    /** Limita al club del principal (`club_id = principal.clubId`). */
    CLUB,

    /** Limita a las filas cuyo propietario es el propio principal. */
    OWNED,

    /** Limita a los grupos que entrena el principal (rol Entrenador). */
    GRUPOS_DEL_ENTRENADOR,

    /** Limita a los grupos a los que pertenece el principal (rol Alumno). */
    MIS_GRUPOS,
}

/**
 * Declara el ámbito de filtrado que aplica un método `@Repository` (ADR-0009 D6). ArchUnit
 * (`AuthorizationArchTest`) exige que **todo** método público de un repositorio lleve
 * [AuthScope] o [NoAuthScope]: así ningún acceso a datos escapa por descuido a la malla anti-IDOR.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AuthScope(
    vararg val scopes: Scope,
)

/**
 * Exime explícitamente a un método de repositorio de llevar [AuthScope] (ej. consultas internas
 * sin datos de cliente o agregados de sistema). Obliga a justificar la decisión por escrito.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class NoAuthScope(
    val justificacion: String,
)
