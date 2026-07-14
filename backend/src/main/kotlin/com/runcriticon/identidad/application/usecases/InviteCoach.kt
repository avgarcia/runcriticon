package com.runcriticon.identidad.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.api.events.EntrenadorInvitado
import com.runcriticon.identidad.application.InvitationIssuer
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.events.UserInvited
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.observability.OpenTelemetryHelper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional

/**
 * Alta de un entrenador por invitación (ADR-0003 D3, CA principal de LAL-7). Solo el ADMIN puede
 * ejecutarlo (ADR-0009). La orquestación (crear usuario `INVITADO`, emitir token, email vía outbox,
 * auditoría) vive en [InvitationIssuer], compartida con [InviteStudent], [ResendInvitation] y
 * [ResendStudentInvitation] — este cascarón autoriza, fija `role = ENTRENADOR` y traduce el domain
 * event [UserInvited] (ADR-0008 D2/D4) al integration event público [EntrenadorInvitado] (LAL-54),
 * simétrico al [com.runcriticon.identidad.api.events.AlumnoInvitado] que publica [InviteStudent].
 */
@ApplicationService
class InviteCoach(
    private val invitationIssuer: InvitationIssuer,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun execute(
        actor: Principal,
        name: String,
        emailRaw: String,
    ): Either<IdentidadError, UserId> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.COACH, Action.INVITE)) {
                IdentidadError.Forbidden
            }
            val invited = invitationIssuer.issue(actor, name, emailRaw, Role.ENTRENADOR).bind()
            publishEntrenadorInvitado(invited)
            invited.user.id
        }

    /** Traduce el domain event [UserInvited] al integration event [EntrenadorInvitado] para otros módulos. */
    private fun publishEntrenadorInvitado(invited: UserInvited) {
        eventPublisher.publishEvent(
            EntrenadorInvitado(
                eventId = UuidCreator.getTimeOrderedEpoch(),
                aggregateId = invited.user.id.value,
                occurredAt = invited.occurredAt,
                clubId = invited.user.clubId.value,
                actorId = invited.actorId,
                traceparent = OpenTelemetryHelper.actualTraceparent(),
                name = invited.user.name,
                email = invited.user.email.value,
            ),
        )
    }
}
