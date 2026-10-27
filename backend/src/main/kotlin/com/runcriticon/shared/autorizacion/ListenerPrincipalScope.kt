package com.runcriticon.shared.autorizacion

import com.runcriticon.shared.autorizacion.model.Principal

/**
 * Da a un listener de eventos el [Principal] que una petición tiene de serie. Un `@ApplicationModuleListener` corre
 * asíncrono tras el commit —o en un reintento del outbox— sin sesión ni `SecurityContext`, así que cualquier
 * repositorio con `@AuthScope(Scope.CLUB)` que alcance falla cerrado en `AuthScopeEnforcementAspect`: el listener
 * revienta siempre y deja el evento sin completar.
 *
 * [runAs] ejecuta el bloque con [Principal] como principal en curso y restaura después el contexto que hubiera. El
 * aspecto sigue verificando que el `clubId` de cada llamada coincide con el del principal, igual que en una petición:
 * no se salta ninguna comprobación. El principal lo construye el listener a partir del propio evento (su `clubId` y su
 * actor), nunca de una entrada de usuario.
 *
 * **Solo para listeners**: desde una petición serviría para suplantar al usuario autenticado. `AuthorizationArchTest`
 * verifica que solo lo usan clases de `..application.listeners..`.
 *
 * Alternativa que se descartó, para que no se reintroduzca: marcar `@NoAuthScope` los métodos de repositorio que
 * alcanza un listener funciona cuando el método solo lo llama el listener, pero no cuando el listener reutiliza el
 * mismo colaborador que las peticiones (`StudentClassification.classify`): obligaría a duplicar cada repositorio.
 */
interface ListenerPrincipalScope {
    fun <T> runAs(
        principal: Principal,
        block: () -> T,
    ): T
}
