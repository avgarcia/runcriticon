package com.runcriticon.identidad.application.usecases.invitation

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.identidad.application.InvitationIssuer
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.autorizacion.model.Role
import org.springframework.transaction.annotation.Transactional

/**
 * Reenvío de invitación a un entrenador existente. Solo el ADMIN puede ejecutarlo. La orquestación (rate-limit,
 * rotación de token, email vía outbox, auditoría) vive en [InvitationIssuer], compartida con [InviteCoachCommand],
 * [InviteStudentCommand] y [ResendStudentInvitationCommand] — este cascarón solo autoriza y fija
 * `expectedRole = ENTRENADOR` (antes no se comprobaba, permitiendo reenviar sobre un id de alumno).
 */
@ApplicationService
class ResendInvitationCommand(
    private val invitationIssuer: InvitationIssuer,
) {
    @Transactional
    fun execute(
        actor: Principal,
        coachId: UserId,
    ): Either<IdentidadError, Unit> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.COACH, Action.INVITE)) {
                IdentidadError.Forbidden
            }
            invitationIssuer.reissueFor(actor, coachId, Role.ENTRENADOR).bind()
        }
}
