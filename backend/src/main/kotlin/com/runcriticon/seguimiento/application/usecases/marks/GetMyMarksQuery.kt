package com.runcriticon.seguimiento.application.usecases.marks

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.seguimiento.application.ports.outbound.persistence.StudentMarkRepository
import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.SeguimientoError
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.seguimiento.domain.StudentMark
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional

/** Orden fijo de las cuatro distancias estándar (ADR-0002 D7, wireframe `mis-marcas.html`): mismo orden que
 * pinta la pantalla, ni corto ni alfabético. */
private val STANDARD_DISTANCES =
    listOf(RaceDistance.FIVE_K, RaceDistance.TEN_K, RaceDistance.HALF_MARATHON, RaceDistance.MARATHON)

/**
 * Las propias marcas del alumno (LAL-31): las cuatro distancias estándar, con `null` en las que todavía no
 * tiene valor — el frontend pinta siempre las cuatro cards, vacías o no.
 */
@ApplicationService
class GetMyMarksQuery(
    private val repository: StudentMarkRepository,
) {
    @Transactional(readOnly = true)
    fun execute(actor: Principal): Either<SeguimientoError, Map<RaceDistance, StudentMark?>> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.MARCA, Action.LIST)) {
                SeguimientoError.Forbidden
            }
            val recorded = repository.findAll(ClubId.of(actor.clubId), StudentId.of(actor.userId))
            STANDARD_DISTANCES.associateWith { recorded[it] }
        }
}
