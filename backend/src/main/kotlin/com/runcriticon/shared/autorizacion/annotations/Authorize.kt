package com.runcriticon.shared.autorizacion.annotations

/**
 * Declara la regla de autorización que protege un endpoint de la capa `api`. La [expresion] referencia la matriz de
 * autorización (ej. `"PLAN:CREAR"`). ArchUnit exige que **todo** handler público lleve [Authorize] o [NoAuthRequired]:
 * ningún endpoint queda sin decisión.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Authorize(
    val expresion: String,
)

/**
 * Marca un endpoint o caso de uso como deliberadamente público/anónimo (ej. health check, login, activación por token).
 * Obliga a justificar por escrito por qué no requiere autorización. A nivel de `CLASS` exime el `@ApplicationService`
 * completo (flujo anónimo de punta a punta); a nivel de `FUNCTION` exime un handler REST concreto.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class NoAuthRequired(
    val justificacion: String,
)

/**
 * Marca un endpoint o caso de uso que **requiere sesión activa** (la garantiza la `SecurityFilterChain`) pero al que no
 * le aplica ninguna regla de la [com.runcriticon.shared.autorizacion.AuthorizationMatrix]: solo opera sobre la propia
 * sesión del llamador, sin tocar ningún recurso de terceros (ej. `QueryCurrentSessionQuery`, cierre de la propia
 * sesión). Distinta de [NoAuthRequired]: aquí sí hace falta estar autenticado.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AuthenticatedOnly(
    val justificacion: String,
)
