package com.runcriticon.identidad.application.usecases.invitation

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.identidad.application.ports.outbound.persistence.ClubRepository
import com.runcriticon.identidad.application.ports.outbound.persistence.InvitationRepository
import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.application.ports.outbound.security.TokenHasher
import com.runcriticon.identidad.application.ratelimit.RateLimitMetrics
import com.runcriticon.identidad.application.ratelimit.RateLimitScope
import com.runcriticon.identidad.application.ratelimit.RateLimiter
import com.runcriticon.identidad.application.ratelimit.consumeForIp
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.invitation.RawToken
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.annotations.NoAuthRequired
import com.runcriticon.shared.autorizacion.model.Role
import java.time.Instant

/**
 * Resuelve los detalles de una invitación por su token, para la tarjeta de contexto de la pantalla de activación
 * (LAL-64). Público y anónimo -- igual que
 * [com.runcriticon.identidad.application.usecases.account.ActivateAccountCommand], al que precede en el flujo: el
 * invitado la consulta ANTES de enviar el formulario de activación.
 *
 * Solo lectura: valida que la invitación siga usable ([com.runcriticon.identidad.domain.invitation.Invitation.ensureUsable],
 * sin verificar el hash de nuevo -- ya se localizó por él) pero no la consume ni muta nada.
 */
@ApplicationService
@NoAuthRequired("Consulta pública: el invitado aún no tiene sesión, se identifica con el token del email")
class ResolveInvitationQuery(
    private val invitationRepository: InvitationRepository,
    private val userRepository: UserRepository,
    private val clubRepository: ClubRepository,
    private val tokenHasher: TokenHasher,
    private val rateLimiter: RateLimiter,
    private val rateLimitMetrics: RateLimitMetrics,
) {
    fun execute(
        rawToken: String,
        clientIp: String,
    ): Either<IdentidadError, InvitationDetails> =
        either {
            consumeForIp(
                rateLimiter,
                rateLimitMetrics,
                RateLimitScope.INVITATION_LOOKUP_IP,
                METRIC_LABEL,
                clientIp,
            )
            ensure(rawToken.isNotBlank()) { IdentidadError.InvalidInput("token", "required") }

            val tokenHash = tokenHasher.hash(RawToken(rawToken))
            val invitation = invitationRepository.findByTokenHash(tokenHash)
            ensureNotNull(invitation) { IdentidadError.InvalidInput("token", "mismatch") }
            invitation.ensureUsable(Instant.now()).bind()

            val invited = userRepository.findByIdUnscoped(invitation.clubId, invitation.userId)
            ensureNotNull(invited) { IdentidadError.NotFound }

            val club = clubRepository.findByIdUnscoped(invitation.clubId)
            ensureNotNull(club) { IdentidadError.NotFound }

            val inviter = invitation.invitedBy?.let { userRepository.findByIdUnscoped(invitation.clubId, it) }

            InvitationDetails(
                name = invited.name,
                club = club.name,
                role = invited.role,
                invitedBy = inviter?.name,
            )
        }

    private companion object {
        const val METRIC_LABEL = "invitacion_consulta"
    }
}

/** Resultado de solo lectura para la tarjeta de contexto de activación (LAL-64). */
data class InvitationDetails(
    val name: String,
    val club: String,
    val role: Role,
    val invitedBy: String?,
)
