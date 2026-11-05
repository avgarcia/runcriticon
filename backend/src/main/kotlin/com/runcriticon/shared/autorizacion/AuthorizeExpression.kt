package com.runcriticon.shared.autorizacion

import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Resource

/** Par recurso-acción de la [AuthorizationMatrix] al que apunta una expresión de `@Authorize`. */
data class Permission(
    val resource: Resource,
    val action: Action,
)

/**
 * Traduce la expresión de `@Authorize("RECURSO:ACCION")` a su [Permission]. La expresión es un string que el compilador
 * no verifica (ADR-0009 D2): devuelve `null` si no tiene esa forma o si el recurso o la acción no existen, y quien la
 * evalúa deniega en ese caso.
 */
object AuthorizeExpression {
    fun parse(expresion: String): Permission? {
        val parts = expresion.split(":")
        if (parts.size != 2) return null
        val resource = Resource.entries.firstOrNull { it.name == parts[0] }
        val action = Action.entries.firstOrNull { it.name == parts[1] }
        return if (resource != null && action != null) Permission(resource, action) else null
    }
}
