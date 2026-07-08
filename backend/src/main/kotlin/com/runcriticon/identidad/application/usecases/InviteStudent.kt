package com.runcriticon.identidad.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.identidad.api.events.AlumnoInvitado
import com.runcriticon.identidad.application.InvitationIssuer
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.User
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
import java.time.Instant
import java.util.UUID

/**
 * Alta de un alumno por invitación (CA principal de LAL-8, ADR-0003 D3). Lo ejecuta un ADMIN o un
 * ENTRENADOR — la delegación a entrenadores reparte la carga de registro (ADR-0009). La orquestación
 * compartida (crear usuario `INVITADO`, emitir token, email vía outbox, auditoría) vive en
 * [InvitationIssuer], compartida con [InviteCoach], [ResendInvitation] y [ResendStudentInvitation].
 *
 * Lo único propio de este cascarón: fija `role = ALUMNO` y publica el integration event
 * [AlumnoInvitado] para otros módulos — [InvitationIssuer] no lo conoce, ya que ni [InviteCoach] ni
 * los reenvíos lo emiten.
 */
@ApplicationService
class InviteStudent(
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
            val user = invitationIssuer.issue(actor, name, emailRaw, Role.ALUMNO).bind()
            publishAlumnoInvitado(actor, user)
            user.id
        }

    /** Publica el integration event [AlumnoInvitado] para que otros módulos siembren su proyección local. */
    private fun publishAlumnoInvitado(
        actor: Principal,
        user: User,
    ) {
        eventPublisher.publishEvent(
            AlumnoInvitado(
                eventId = UUID.randomUUID(),
                aggregateId = user.id.value,
                occurredAt = Instant.now(),
                clubId = actor.clubId,
                actorId = actor.userId,
                traceparent = OpenTelemetryHelper.actualTraceparent(),
                name = user.name,
                email = user.email.value,
            ),
        )
    }
}
