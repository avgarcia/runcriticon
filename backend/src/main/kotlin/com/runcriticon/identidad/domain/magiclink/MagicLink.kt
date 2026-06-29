package com.runcriticon.identidad.domain.magiclink

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.invitation.TokenHash
import com.runcriticon.identidad.domain.user.UserId
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Agregado de magic link de un solo uso (ADR-0003 D5): enlace de login enviado al email de un usuario
 * **ya activo**, válido 15 minutos. Al usarse queda consumido y no puede reutilizarse aunque el
 * atacante tenga el enlace. Espejo de [com.runcriticon.identidad.domain.invitation.Invitation] con un
 * TTL más corto y semántica de login (no de activación).
 *
 * Dominio puro (ADR-0008): no conoce el secreto ni el algoritmo de hashing; guarda solo el [tokenHash]
 * (HMAC calculado en infraestructura) y lo compara en tiempo constante. El tiempo entra como
 * parámetro [Instant]: el dominio nunca llama a `Instant.now()`.
 */
data class MagicLink(
    val id: MagicLinkId,
    val userId: UserId,
    val clubId: UUID,
    val tokenHash: TokenHash,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val consumedAt: Instant?,
) {
    /**
     * Consume el magic link verificando el token presentado (ya hasheado por infraestructura).
     * Invariantes (ADR-0003 D5):
     *  - un solo uso → [IdentidadError.Conflict] si ya estaba consumido;
     *  - caducidad → [IdentidadError.InvalidInput] si `now` supera [expiresAt] (>15 min);
     *  - verificación → [IdentidadError.InvalidInput] si el hash no coincide (comparación timing-safe).
     */
    fun consume(
        presentedTokenHash: TokenHash,
        now: Instant,
    ): Either<IdentidadError, MagicLink> =
        either {
            ensure(consumedAt == null) { IdentidadError.Conflict("el enlace ya fue usado") }
            ensure(!now.isAfter(expiresAt)) { IdentidadError.InvalidInput("magicLink", "expired") }
            ensure(tokenHash.matches(presentedTokenHash)) { IdentidadError.InvalidInput("token", "mismatch") }
            copy(consumedAt = now)
        }

    companion object {
        /** Caducidad por defecto desde la emisión (ADR-0003 D5: 15 minutos). */
        val DEFAULT_TTL: Duration = Duration.ofMinutes(15)

        /**
         * Emite un magic link nuevo, abierto y con caducidad `now + ttl`. El [tokenHash] llega ya
         * calculado (HMAC en infraestructura); el texto claro nunca entra al dominio.
         */
        fun issue(
            userId: UserId,
            clubId: UUID,
            tokenHash: TokenHash,
            now: Instant,
            ttl: Duration = DEFAULT_TTL,
        ): MagicLink {
            require(ttl > Duration.ZERO) { "el ttl del magic link debe ser positivo" }
            return MagicLink(
                id = MagicLinkId.new(),
                userId = userId,
                clubId = clubId,
                tokenHash = tokenHash,
                issuedAt = now,
                expiresAt = now.plus(ttl),
                consumedAt = null,
            )
        }
    }
}
