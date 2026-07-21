package com.runcriticon.identidad.application.usecases.club

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.runcriticon.identidad.application.ports.outbound.persistence.ClubRepository
import com.runcriticon.identidad.domain.club.Club
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional

/**
 * `PATCH /api/club`: cambia el nombre del club. Solo el ADMIN. Opera siempre sobre `actor.clubId` — nunca sobre un id
 * recibido del cliente, por eso el endpoint no lleva `{id}` en la ruta.
 */
@ApplicationService
class UpdateClubCommand(
    private val clubRepository: ClubRepository,
) {
    @Transactional
    fun execute(
        actor: Principal,
        name: String,
    ): Either<IdentidadError, Club> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.CLUB, Action.UPDATE)) {
                IdentidadError.Forbidden
            }
            val club = clubRepository.findById(ClubId.of(actor.clubId))
            ensureNotNull(club) { IdentidadError.NotFound }
            val renamed = club.rename(name).bind()
            clubRepository.save(renamed)
            renamed
        }
}
