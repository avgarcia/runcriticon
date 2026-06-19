package com.runcriticon.shared.autorizacion.model

import org.springframework.security.core.AuthenticatedPrincipal
import java.io.Serializable
import java.util.UUID

/**
 * El usuario autenticado en curso (ADR-0009 D6). Vive en el núcleo compartido y lo
 * obtiene cada caso de uso a través de [PrincipalProvider].
 *
 * Implementa [AuthenticatedPrincipal] para que `Authentication.getName()` —y por tanto el
 * `PRINCIPAL_NAME` que indexa Spring Session— sea el `userId` y no el `toString()` completo (que
 * desbordaría la columna). Es [Serializable] porque viaja en el `SecurityContext` que Spring
 * Session JDBC persiste en Postgres (ADR-0003 D10).
 */
data class Principal(
    val userId: UUID,
    val clubId: UUID,
    val role: Role,
) : AuthenticatedPrincipal,
    Serializable {
    override fun getName(): String = userId.toString()

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
