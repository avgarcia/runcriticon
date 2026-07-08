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
 * Reinvitación de un alumno existente (LAL-8, paridad con la reinvitación de entrenador). La pueden
 * ejecutar admin y entrenador (delegación, ADR-0003 D3; ADR-0009). La orquestación vive en
 * [InvitationIssuer], compartida con [InviteCoach], [InviteStudent] y [ResendInvitation] — este
 * cascarón solo autoriza y fija `expectedRole = ALUMNO`.
 *
 * A diferencia de [InviteStudent], **no** publica `AlumnoInvitado`: la reinvitación no es un alta.
 */
@ApplicationService
class ResendStudentInvitation(
    private val invitationIssuer: InvitationIssuer,
) {
    @Transactional
    fun execute(
        actor: Principal,
        studentId: UserId,
    ): Either<IdentidadError, Unit> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.STUDENT, Action.INVITE)) {
                IdentidadError.Forbidden
            }
            invitationIssuer.reissueFor(actor, studentId, Role.ALUMNO).bind()
        }
}
