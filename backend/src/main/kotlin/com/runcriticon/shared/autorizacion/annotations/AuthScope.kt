package com.runcriticon.shared.autorizacion.annotations

/**
 * Ámbito de filtrado que un método de repositorio aplica sobre sus consultas para no devolver filas fuera del alcance
 * del [Principal].
 *
 * Solo existe [CLUB], el único que `AuthScopeEnforcementAspect` sabe verificar (ADR-0009 D11). Los scopes de relación
 * que preveía el diseño original (propietario, grupos del entrenador, grupos del alumno) se retiraron: el aspecto no los
 * verificaba y declararlos convertía cada llamada en un 403. La relación del principal con el objeto la comprueba el
 * caso de uso (D3, D7). Un scope nuevo solo se añade junto con su verificación en el aspecto.
 */
enum class Scope {
    /** Limita al club del principal (`club_id = principal.clubId`). */
    CLUB,
}

/**
 * Declara el ámbito de filtrado que aplica un método `@Repository`. ArchUnit (`AuthorizationArchTest`) exige que
 * **todo** método público de un repositorio lleve [AuthScope] o [NoAuthScope]: así ningún acceso a datos escapa por
 * descuido a la malla anti-IDOR.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AuthScope(
    vararg val scopes: Scope,
)

/**
 * Exime explícitamente a un método de repositorio de llevar [AuthScope] (ej. consultas internas sin datos de cliente o
 * agregados de sistema). Obliga a justificar la decisión por escrito.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class NoAuthScope(
    val justificacion: String,
)
