package com.runcriticon.shared.autorizacion.model

import org.springframework.security.core.AuthenticatedPrincipal
import java.io.Serializable
import java.util.UUID

/**
 * El usuario autenticado en curso. Vive en el núcleo compartido y lo obtiene cada caso de uso a través de
 * [PrincipalProvider].
 *
 * Implementa [AuthenticatedPrincipal] para que `Authentication.getName()` —y por tanto el `PRINCIPAL_NAME` que indexa
 * Spring Session— sea el `userId` y no el `toString()` completo (que desbordaría la columna). Es [Serializable] porque
 * viaja en el `SecurityContext` que Spring Session JDBC persiste en Postgres.
 *
 * [userId] y [clubId] van como `UUID` crudo a propósito: cambiar el tipo de un campo rompería la deserialización Java
 * de las sesiones ya persistidas.
 *
 * Quien necesite el typed ID envuelve con [ClubId.of].
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
