package com.runcriticon.identidad.domain.magiclink

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.invitation.TokenHash
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.tenancy.ClubId
import java.time.Duration
import java.time.Instant

/**
 * Agregado de magic link de un solo uso: enlace enviado al email de un usuario **ya activo**, válido 15 minutos. Al
 * usarse queda consumido y no puede reutilizarse aunque el atacante tenga el enlace. Espejo de
 * [com.runcriticon.identidad.domain.invitation.Invitation] con un TTL más corto y semántica de login/reseteo
 * (no de activación).
 *
 * El [proposito] discrimina para qué se emitió el token ([MagicLinkPurpose.LOGIN] o [MagicLinkPurpose.RESETEO]):
 * [consume] exige el propósito esperado, así un enlace de login no vale como reseteo ni al revés (aislamiento de
 * propósito).
 *
 * Dominio puro: no conoce el secreto ni el algoritmo de hashing; guarda solo el [tokenHash] (HMAC calculado en
 * infraestructura) y lo compara en tiempo constante. El tiempo entra como parámetro [Instant]: el dominio nunca llama a
 * `Instant.now()`.
 */
data class MagicLink(
    val id: MagicLinkId,
    val userId: UserId,
    val clubId: ClubId,
    val tokenHash: TokenHash,
    val proposito: MagicLinkPurpose,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val consumedAt: Instant?,
) {
    /**
     * Consume el magic link verificando propósito y token presentado (ya hasheado por infraestructura).
     * Invariantes:
     *  - aislamiento de propósito → [IdentidadError.InvalidInput] si [proposito] != [expectedPurpose];
     *  - un solo uso → [IdentidadError.Conflict] si ya estaba consumido;
     *  - caducidad → [IdentidadError.InvalidInput] si `now` supera [expiresAt] (>15 min);
     *  - verificación → [IdentidadError.InvalidInput] si el hash no coincide (comparación timing-safe).
     */
    fun consume(
        expectedPurpose: MagicLinkPurpose,
        presentedTokenHash: TokenHash,
        now: Instant,
    ): Either<IdentidadError, MagicLink> =
        either {
            ensure(proposito == expectedPurpose) { IdentidadError.InvalidInput("magicLink", "purpose_mismatch") }
            ensure(consumedAt == null) { IdentidadError.Conflict("el enlace ya fue usado") }
            ensure(!now.isAfter(expiresAt)) { IdentidadError.InvalidInput("magicLink", "expired") }
            ensure(tokenHash.matches(presentedTokenHash)) { IdentidadError.InvalidInput("token", "mismatch") }
            copy(consumedAt = now)
        }

    companion object {
        /** Caducidad por defecto desde la emisión. */
        val DEFAULT_TTL: Duration = Duration.ofMinutes(15)

        /**
         * Emite un magic link nuevo, abierto y con caducidad `now + ttl`, para el [proposito] indicado. El [tokenHash]
         * llega ya calculado (HMAC en infraestructura); el texto claro nunca entra al dominio.
         */
        fun issue(
            userId: UserId,
            clubId: ClubId,
            tokenHash: TokenHash,
            proposito: MagicLinkPurpose,
            now: Instant,
            ttl: Duration = DEFAULT_TTL,
        ): MagicLink {
            require(ttl > Duration.ZERO) { "el ttl del magic link debe ser positivo" }
            return MagicLink(
                id = MagicLinkId.new(),
                userId = userId,
                clubId = clubId,
                tokenHash = tokenHash,
                proposito = proposito,
                issuedAt = now,
                expiresAt = now.plus(ttl),
                consumedAt = null,
            )
        }
    }
}
