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
            // Listar alumnos: ADMIN y ENTRENADOR ven hoy el mismo listado, el club entero — la relación
            // entrenador↔alumno todavía no existe, mismo hueco que GROUP:LIST documenta para grupos.
            Triple(Role.ADMIN, Resource.STUDENT, Action.LIST),
            Triple(Role.ENTRENADOR, Resource.STUDENT, Action.LIST),
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
            // Ajustar a mano quién está en un grupo es modificar el grupo, no clasificar al alumno: no toca sus tags.
            // Va con UPDATE, que comparten ADMIN y ENTRENADOR: el entrenador es quien arma los grupos con los que
            // trabaja, y tocar quién está dentro no cambia quién puede publicarle un plan.
            Triple(Role.ADMIN, Resource.GROUP, Action.UPDATE),
            Triple(Role.ENTRENADOR, Resource.GROUP, Action.UPDATE),
            // Asignar entrenadores a un grupo es otra cosa (LAL-93): esta relación SÍ decide quién puede publicar
            // planes al grupo (AC2, pendiente de Planificación). Por eso no cupo en UPDATE pese a que un comentario
            // anterior en este mismo fichero lo daba por hecho — dejarla ahí habría permitido que un entrenador se
            // autoasignara a cualquier grupo y se concediera a sí mismo el permiso que ese AC debía negarle. Solo
            // ADMIN.
            Triple(Role.ADMIN, Resource.GROUP, Action.ASSIGN_COACH),
            // Crear un plan en borrador es un acto operativo de quien entrena, no de quien administra el club — el
            // admin no aparece aquí a propósito (LAL-114 no lo pide; si emerge la necesidad, se añade con esa
            // historia). La comprobación de que el entrenador tiene relación con el grupo del plan va en el caso de
            // uso (CoachGroupLookup), no en esta matriz: RBAC decide el rol, no el objeto concreto.
            Triple(Role.ENTRENADOR, Resource.PLAN, Action.CREATE),
            Triple(Role.ENTRENADOR, Resource.PLAN, Action.LIST),
            // Componer las sesiones de un plan (LAL-24: alta, edición y borrado) es la misma operación de quien
            // entrena que crearlo — mismo criterio que CREATE/LIST de arriba, ADMIN no aparece a propósito. La
            // relación con el grupo la revalida el caso de uso contra `CoachGroupLookup` en cada mutación (un
            // entrenador expulsado del grupo no debe seguir editando sus planes viejos), no esta matriz.
            Triple(Role.ENTRENADOR, Resource.PLAN, Action.UPDATE),
            // Publicar es del mismo entrenador que crea y edita el plan — ADMIN no aparece por el mismo criterio
            // que CREATE/UPDATE. La relación con el grupo (AC3) y la frescura de la proyección de membresía
            // (ADR-0009 D9) las revalida el caso de uso, no esta matriz.
            Triple(Role.ENTRENADOR, Resource.PLAN, Action.PUBLISH),
            // Consulta forense del log de auditoría (ADR-0009 D17): solo ADMIN, ni ENTRENADOR ni ALUMNO.
            Triple(Role.ADMIN, Resource.AUDIT_EVENT, Action.LIST),
            // Ver la propia semana resuelta (LAL-29): primera regla de ALUMNO en esta matriz. Solo el propio
            // alumno — no hay ADMIN ni ENTRENADOR aquí porque `GET /me/plan` no acepta un alumnoId de entrada,
            // siempre es el del Principal (ver GetMyWeekQuery).
            Triple(Role.ALUMNO, Resource.RESOLVED_SESSION, Action.LIST),
            // Reportar una sesión (LAL-30): igual que arriba, solo el propio alumno — `PUT /me/reportes/{dia}`
            // tampoco acepta un alumnoId de entrada.
            Triple(Role.ALUMNO, Resource.SESSION_REPORT, Action.SUBMIT),
            // Consentimiento de datos de salud (LAL-128): solo el propio alumno, sobre sí mismo —
            // `/me/consentimiento` no acepta un usuarioId de entrada. ADMIN/ENTRENADOR no aparecen: no
            // son interesados de datos de salud, así que no hay nada que consentir ni revocar.
            Triple(Role.ALUMNO, Resource.CONSENT, Action.GRANT),
            Triple(Role.ALUMNO, Resource.CONSENT, Action.REVOKE),
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
