package com.runcriticon.identidad.domain.consent

import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.tenancy.ClubId
import java.time.Instant

/**
 * Consentimiento explícito de datos de salud del art. 9.2.a RGPD (ADR-0014 D16/D18). Solo lo concede el
 * ALUMNO — es el único interesado de los datos de salud que captura `seguimiento.reporte_sesion`.
 *
 * **Una fila por concesión**, nunca una fila por usuario: conceder tras revocar crea una fila nueva en
 * vez de reescribir la anterior (ver `RGPD.md` del módulo para la justificación frente a la guía
 * operativa, que propone un `UNIQUE (usuario_id, version_texto)` incompatible con este ciclo).
 * `revoke()` sí muta esta misma fila — pero solo rellena `revokedAt`, que nace `null`: nunca sobrescribe
 * `grantedAt` ni ningún otro dato de la concesión original.
 */
data class Consent(
    val id: ConsentId,
    val userId: UserId,
    val clubId: ClubId,
    val textVersion: String,
    val grantedAt: Instant,
    val revokedAt: Instant? = null,
    val ip: String,
    val userAgent: String,
) {
    fun isActive(): Boolean = revokedAt == null

    /** Revoca esta concesión. Solo tiene sentido sobre una fila todavía activa. */
    fun revoke(now: Instant): Consent {
        require(revokedAt == null) { "este consentimiento ya está revocado" }
        return copy(revokedAt = now)
    }

    companion object {
        fun grant(
            userId: UserId,
            clubId: ClubId,
            textVersion: String,
            ip: String,
            userAgent: String,
            now: Instant,
        ): Consent =
            Consent(
                id = ConsentId.new(),
                userId = userId,
                clubId = clubId,
                textVersion = textVersion,
                grantedAt = now,
                ip = ip,
                userAgent = userAgent,
            )
    }
}
