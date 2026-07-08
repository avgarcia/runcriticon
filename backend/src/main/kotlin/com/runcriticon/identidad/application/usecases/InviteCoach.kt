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
 * Alta de un entrenador por invitación (ADR-0003 D3, CA principal de LAL-7). Solo el ADMIN puede
 * ejecutarlo (ADR-0009). La orquestación (crear usuario `INVITADO`, emitir token, email vía outbox,
 * auditoría) vive en [InvitationIssuer], compartida con [InviteStudent], [ResendInvitation] y
 * [ResendStudentInvitation] — este cascarón solo autoriza y fija `role = ENTRENADOR`.
 */
@ApplicationService
class InviteCoach(
    private val invitationIssuer: InvitationIssuer,
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
            invitationIssuer.issue(actor, name, emailRaw, Role.ENTRENADOR).bind().id
        }
}
