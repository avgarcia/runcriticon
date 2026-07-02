package com.runcriticon.shared.autorizacion.spring

import com.runcriticon.shared.autorizacion.SessionRevoker
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.Session
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Adaptador de [SessionRevoker] sobre Spring Session (ADR-0003 D7/D8/D11). Borra todas las sesiones
 * activas de un usuario en el almacén JDBC (Postgres). Vive en `shared.autorizacion.spring`, único
 * sitio autorizado a tocar seguridad/sesión (lo verifica `AuthorizationArchTest`), junto a
 * [SecuritySessionManager].
 *
 * **Casado del índice** (riesgo clave de LAL-12): Spring Session indexa cada fila por `PRINCIPAL_NAME`
 * = `Authentication.getName()`. [SecuritySessionManager.startSession] guarda un
 * `UsernamePasswordAuthenticationToken` cuyo principal es
 * [com.runcriticon.shared.autorizacion.model.Principal], que implementa `AuthenticatedPrincipal` con
 * `getName() = userId.toString()`. Por eso `findByPrincipalName(userId)` encuentra —y este adaptador
 * borra— exactamente las sesiones de ese usuario. El test de integración de LAL-12 lo verifica
 * extremo a extremo antes de darlo por bueno.
 */
@Component
class SpringSessionRevoker(
    private val sessionRepository: FindByIndexNameSessionRepository<out Session>,
) : SessionRevoker {
    override fun revokeAll(userId: UUID) {
        sessionRepository
            .findByPrincipalName(userId.toString())
            .keys
            .forEach(sessionRepository::deleteById)
    }
}
