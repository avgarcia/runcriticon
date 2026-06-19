package com.runcriticon.shared.autorizacion

import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.autorizacion.model.Role

/**
 * Matriz de autorización RBAC (ADR-0009 D6, ADR-0003 D2). Es la única fuente de verdad de
 * "qué [Role] puede ejecutar qué [Action] sobre qué [Resource]". Se consulta desde el guardado
 * de cada caso de uso, nunca desde el dominio.
 *
 * En H0 el conjunto de reglas está vacío: las reglas se declaran por feature en Fase 1. El
 * default es deny (si no hay regla explícita, [can] devuelve `false`).
 */
object AuthorizationMatrix {
    private val rules: Set<Triple<Role, Resource, Action>> = emptySet()

    fun can(
        role: Role,
        resource: Resource,
        action: Action,
    ): Boolean = Triple(role, resource, action) in rules
}
