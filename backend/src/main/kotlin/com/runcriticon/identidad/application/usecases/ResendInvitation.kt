package com.runcriticon.identidad.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.identidad.application.InvitationIssuer
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.autorizacion.model.Role
import org.springframework.transaction.annotation.Transactional

/**
 * Reenvío de invitación a un entrenador existente (CA 3 de LAL-47). Solo el ADMIN puede ejecutarlo
 * (ADR-0009). La orquestación (rate-limit, rotación de token ADR-0003 D4, email vía outbox,
 * auditoría) vive en [InvitationIssuer], compartida con [InviteCoach], [InviteStudent] y
 * [ResendStudentInvitation] — este cascarón solo autoriza y fija `expectedRole = ENTRENADOR`
 * (LAL-62: antes no se comprobaba, permitiendo reenviar sobre un id de alumno).
 */
@ApplicationService
class ResendInvitation(
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
