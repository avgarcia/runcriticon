package com.runcriticon.identidad.domain.invitation

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.autorizacion.model.ClubId
import java.time.Duration
import java.time.Instant

/**
 * Agregado de invitación de un solo uso (ADR-0003 D4, D13). Se emite al crear una cuenta y se envía
 * por email; al usarse queda consumida y no puede reutilizarse aunque el atacante tenga el enlace.
 *
 * Dominio puro (ADR-0008): no conoce el secreto de aplicación ni el algoritmo de hashing. Guarda
 * solo el [tokenHash] (HMAC calculado en infraestructura) y lo compara en tiempo constante. El
 * tiempo entra como parámetro [Instant]: el dominio nunca llama a `Instant.now()`.
 *
 * `consumedAt` modela "invitación cerrada / no usable": se fija tanto al activar como al ser
 * reemplazada por una reinvitación ([reissue]).
 */
data class Invitation(
    val id: InvitationId,
    val userId: UserId,
    val clubId: ClubId,
    val tokenHash: TokenHash,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val consumedAt: Instant?,
) {
    /**
     * Consume la invitación verificando el token presentado (ya hasheado por infraestructura).
     * Invariantes (ADR-0003 D4):
     *  - un solo uso → [IdentidadError.Conflict] si ya estaba consumida;
     *  - caducidad → [IdentidadError.InvalidInput] si `now` supera [expiresAt];
     *  - verificación → [IdentidadError.InvalidInput] si el hash no coincide (comparación timing-safe).
     */
    fun consume(
        presentedTokenHash: TokenHash,
        now: Instant,
    ): Either<IdentidadError, Invitation> =
        either {
            ensure(consumedAt == null) { IdentidadError.Conflict("la invitación ya fue consumida") }
            ensure(!now.isAfter(expiresAt)) { IdentidadError.InvalidInput("invitation", "expired") }
            ensure(tokenHash.matches(presentedTokenHash)) { IdentidadError.InvalidInput("token", "mismatch") }
            copy(consumedAt = now)
        }

    /**
     * Reinvitación (ADR-0003 D4): emitir un nuevo token invalida el anterior aunque no haya caducado.
     * Devuelve la pareja (invitación anterior invalidada, invitación nueva utilizable). Si la anterior
     * ya estaba cerrada, conserva su `consumedAt` original.
     */
    fun reissue(
        newTokenHash: TokenHash,
        now: Instant,
        ttl: Duration = DEFAULT_TTL,
    ): Pair<Invitation, Invitation> {
        val invalidated = copy(consumedAt = consumedAt ?: now)
        val fresh = issue(userId, clubId, newTokenHash, now, ttl)
        return invalidated to fresh
    }

    companion object {
        /** Caducidad por defecto desde la emisión (ADR-0003 D4: 7 días). */
        val DEFAULT_TTL: Duration = Duration.ofDays(7)

        /**
         * Emite una invitación nueva, abierta y con caducidad `now + ttl`. El [tokenHash] llega ya
         * calculado (HMAC en infraestructura); el texto claro nunca entra al dominio.
         */
        fun issue(
            userId: UserId,
            clubId: ClubId,
            tokenHash: TokenHash,
            now: Instant,
            ttl: Duration = DEFAULT_TTL,
        ): Invitation {
            require(ttl > Duration.ZERO) { "el ttl de la invitación debe ser positivo" }
            return Invitation(
                id = InvitationId.new(),
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
