package com.runcriticon.shared.autorizacion

import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.autorizacion.model.Role

/**
 * Matriz de autorización RBAC (ADR-0009 D6, ADR-0003 D2). Es la única fuente de verdad de
 * "qué [Role] puede ejecutar qué [Action] sobre qué [Resource]". Se consulta desde el guardado
 * de cada caso de uso, nunca desde el dominio.
 *
 * Las reglas se declaran por feature en Fase 1. El default es deny (si no hay regla explícita,
 * [can] devuelve `false`).
 */
object AuthorizationMatrix {
    private val rules: Set<Triple<Role, Resource, Action>> =
        setOf(
            // Solo el ADMIN da de alta entrenadores (ADR-0009; LAL-46).
            Triple(Role.ADMIN, Resource.COACH, Action.INVITE),
            // Admin y entrenador dan de alta alumnos (delegación a entrenadores, ADR-0003 D3; LAL-8).
            Triple(Role.ADMIN, Resource.STUDENT, Action.INVITE),
            Triple(Role.ENTRENADOR, Resource.STUDENT, Action.INVITE),
            // Solo el ADMIN lista entrenadores y gestiona sesiones/estado de un usuario (ADR-0003 D11; LAL-13).
            Triple(Role.ADMIN, Resource.COACH, Action.LIST),
            Triple(Role.ADMIN, Resource.USER, Action.REVOKE_SESSIONS),
            Triple(Role.ADMIN, Resource.USER, Action.DEACTIVATE),
        )

    fun can(
        role: Role,
        resource: Resource,
        action: Action,
    ): Boolean = Triple(role, resource, action) in rules

    /** Todas las acciones concedidas a [role], agrupadas por recurso (ADR-0009 D18, `/me/permissions`). */
    fun grantedTo(role: Role): Map<Resource, Set<Action>> =
        rules
            .filter { (ruleRole, _, _) -> ruleRole == role }
            .groupBy({ (_, resource, _) -> resource }, { (_, _, action) -> action })
            .mapValues { (_, actions) -> actions.toSet() }
}
