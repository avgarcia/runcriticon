package com.runcriticon.identidad.application.usecases.club

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.runcriticon.identidad.application.ports.outbound.persistence.ClubRepository
import com.runcriticon.identidad.domain.club.Club
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.annotations.AuthenticatedOnly
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional

/**
 * `GET /api/club`: consulta la ficha del propio club. Cualquier rol autenticado puede verla — no hay regla en la
 * [com.runcriticon.shared.autorizacion.AuthorizationMatrix] porque no distingue por rol, igual que
 * `QueryMyPermissionsQuery`.
 */
@ApplicationService
@AuthenticatedOnly(
    "Devuelve la ficha del propio club del principal; no hay recurso de terceros que autorizar",
)
class QueryClubQuery(
    private val clubRepository: ClubRepository,
) {
    @Transactional(readOnly = true)
    fun execute(actor: Principal): Either<IdentidadError, Club> =
        either {
            val club = clubRepository.findById(ClubId.of(actor.clubId))
            ensureNotNull(club) { IdentidadError.NotFound }
        }
}
