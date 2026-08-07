package com.runcriticon.shared.autorizacion

import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.autorizacion.model.Role

/**
 * Matriz de autorización RBAC. Es la única fuente de verdad de "qué [Role] puede ejecutar qué [Action] sobre qué
 * [Resource]". Se consulta desde el guardado de cada caso de uso, nunca desde el dominio.
 *
 * Las reglas se declaran por feature. El default es deny (si no hay regla explícita, [can] devuelve `false`).
 */
object AuthorizationMatrix {
    private val rules: Set<Triple<Role, Resource, Action>> =
        setOf(
            Triple(Role.ADMIN, Resource.COACH, Action.INVITE),
            Triple(Role.ADMIN, Resource.STUDENT, Action.INVITE),
            Triple(Role.ENTRENADOR, Resource.STUDENT, Action.INVITE),
            Triple(Role.ADMIN, Resource.COACH, Action.LIST),
            Triple(Role.ADMIN, Resource.USER, Action.REVOKE_SESSIONS),
            Triple(Role.ADMIN, Resource.USER, Action.DEACTIVATE),
            // Supresión: solo el admin. El entrenador da de alta alumnos, pero no puede borrarlos — es irreversible
            // y arrastra el borrado de sus datos en el resto de módulos.
            Triple(Role.ADMIN, Resource.USER, Action.DELETE),
            Triple(Role.ADMIN, Resource.CLUB, Action.UPDATE),
            // Clasificar alumnos: ADMIN y ENTRENADOR. El entrenador ya da de alta alumnos, así que ponerles
            // etiquetas es la continuación natural de esa alta. No le concede gestionar el catálogo de ejes, que
            // sigue siendo del admin: para eso está TAXONOMY:MANAGE.
            Triple(Role.ADMIN, Resource.STUDENT, Action.CLASSIFY),
            Triple(Role.ENTRENADOR, Resource.STUDENT, Action.CLASSIFY),
            // Taxonomía: el admin la gestiona (escritura) y la lista; el entrenador solo la consulta.
            Triple(Role.ADMIN, Resource.TAXONOMY, Action.MANAGE),
            Triple(Role.ADMIN, Resource.TAXONOMY, Action.LIST),
            Triple(Role.ENTRENADOR, Resource.TAXONOMY, Action.LIST),
            // Grupos: los crea y los previsualiza tanto el admin como el entrenador — el entrenador es quien arma
            // los grupos con los que trabaja. El alumno queda fuera: la composición de un grupo no es cosa suya.
            Triple(Role.ADMIN, Resource.GROUP, Action.CREATE),
            Triple(Role.ENTRENADOR, Resource.GROUP, Action.CREATE),
            Triple(Role.ADMIN, Resource.GROUP, Action.LIST),
            Triple(Role.ENTRENADOR, Resource.GROUP, Action.LIST),
        )

    fun can(
        role: Role,
        resource: Resource,
        action: Action,
    ): Boolean = Triple(role, resource, action) in rules

    /** Todas las acciones concedidas a [role], agrupadas por recurso. */
    fun grantedTo(role: Role): Map<Resource, Set<Action>> =
        rules
            .filter { (ruleRole, _, _) -> ruleRole == role }
            .groupBy({ (_, resource, _) -> resource }, { (_, _, action) -> action })
            .mapValues { (_, actions) -> actions.toSet() }
}
