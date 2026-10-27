package com.runcriticon.testing

import com.runcriticon.shared.autorizacion.ListenerPrincipalScope
import com.runcriticon.shared.autorizacion.model.Principal

/**
 * Doble de [ListenerPrincipalScope] para los tests unitarios de listeners: ejecuta el bloque tal cual (sin
 * `SecurityContext`, que esos tests no tienen) y anota con qué principal se pidió, para poder afirmarlo.
 */
class RecordingListenerPrincipalScope : ListenerPrincipalScope {
    val principals: MutableList<Principal> = mutableListOf()

    override fun <T> runAs(
        principal: Principal,
        block: () -> T,
    ): T {
        principals += principal
        return block()
    }
}
