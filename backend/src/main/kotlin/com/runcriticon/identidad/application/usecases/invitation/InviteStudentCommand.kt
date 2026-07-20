package com.runcriticon.identidad.application.usecases.invitation

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.api.events.AlumnoInvitado
import com.runcriticon.identidad.application.InvitationIssuer
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.events.UserInvited
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.observability.OpenTelemetryHelper
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional

/**
 * Alta de un alumno por invitación. Lo ejecuta un ADMIN o un ENTRENADOR — la delegación a entrenadores reparte la carga
 * e registro. La orquestación compartida (crear usuario `INVITADO`, emitir token, email vía outbox, auditoría) vive en
 * [InvitationIssuer], compartida con [InviteCoachCommand], [ResendInvitationCommand] y [ResendStudentInvitationCommand].
 *
 * Lo único propio de este cascarón: fija `role = ALUMNO` y traduce el domain event [UserInvited] al integration event
 * público [AlumnoInvitado] para otros módulos — [InvitationIssuer] no conoce esa traducción, ya que ni
 * [InviteCoachCommand] ni los reenvíos la necesitan.
 */
@ApplicationService
class InviteStudentCommand(
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
            ensure(AuthorizationMatrix.can(actor.role, Resource.STUDENT, Action.INVITE)) {
                IdentidadError.Forbidden
            }
            val invited = invitationIssuer.issue(actor, name, emailRaw, Role.ALUMNO).bind()
            publishAlumnoInvitado(invited)
            invited.user.id
        }

    /** Traduce el domain event [UserInvited] al integration event [AlumnoInvitado] para otros módulos. */
    private fun publishAlumnoInvitado(invited: UserInvited) {
        eventPublisher.publishEvent(
            AlumnoInvitado(
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
