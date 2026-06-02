package com.runcriticon.shared.autorizacion

import java.io.Serializable
import java.util.UUID

/**
 * El usuario autenticado en curso (ADR-0009 D6). Vive en el núcleo compartido y lo
 * obtiene cada caso de uso a través de [PrincipalProvider].
 *
 * Es [Serializable] porque viaja dentro del `SecurityContext` que Spring Session JDBC persiste
 * en Postgres (ADR-0003 D10).
 */
data class Principal(
    val userId: UUID,
    val clubId: UUID,
    val rol: Rol,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
