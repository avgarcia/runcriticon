package com.runcriticon.identidad.application.usecases

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional

/**
 * Listado de los entrenadores del club (LAL-13). Solo el ADMIN puede ejecutarlo (ADR-0009). Es la
 * base de la pantalla de gestión desde la que el admin revoca sesiones o desactiva cuentas. Filtra
 * por el club del principal (anti-IDOR): nunca lista entrenadores de otro club.
 */
@ApplicationService
class ListCoaches(
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun execute(actor: Principal): Either<IdentidadError, List<CoachSummary>> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.COACH, Action.LIST)) {
                IdentidadError.Forbidden
            }
            userRepository
                .listByClubAndRole(ClubId.of(actor.clubId), Role.ENTRENADOR)
                .map { user ->
                    CoachSummary(
                        id = user.id.value,
                        name = user.name,
                        email = user.email.value,
                        status = user.status,
                    )
                }
        }
}
