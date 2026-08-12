package com.runcriticon.clubtaxonomia.application.usecases.coaches

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.CoachDirectory
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.person.CoachWorkload
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional

/**
 * Entrenadores del club con su carga. Solo el ADMIN — es la base para repartir el trabajo, no una pantalla del
 * entrenador sobre sí mismo.
 *
 * No se llama `ListCoachesQuery` pese a ser el nombre natural: `identidad.application.usecases.coach.ListCoachesQuery`
 * ya existe (la gestión de sesión del entrenador) y Spring nombra los beans por el simple class name, sin distinguir
 * paquete — dos clases con el mismo nombre chocan al arrancar el contexto aunque no haya ningún cruce real de
 * módulos, mismo tropiezo que ya documenta `StudentDirectoryController`.
 */
@ApplicationService
class ListCoachWorkloadQuery(
    private val coachDirectory: CoachDirectory,
) {
    @Transactional(readOnly = true)
    fun execute(actor: Principal): Either<ClubTaxonomiaError, List<CoachWorkload>> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.COACH, Action.LIST)) {
                ClubTaxonomiaError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            coachDirectory.listByClub(clubId)
        }
}
