package com.runcriticon.seguimiento.application.usecases.clubhealth

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.seguimiento.application.ports.outbound.persistence.ClubHealthReader
import com.runcriticon.seguimiento.domain.GroupActivity
import com.runcriticon.seguimiento.domain.SeguimientoError
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional

/**
 * Última actividad reportada de cada grupo del club, para la vista de salud del admin.
 *
 * Sin `@AuditAccess`: la lectura es un agregado por grupo (`MAX(reportado_en)`), sin ningún alumno
 * identificable en la respuesta — no hay sujeto de datos que auditar. Devolver el `grupoId` como sujeto
 * escribiría en el log de auditoría un identificador que no es una persona, y el listener que anonimiza
 * ese log al ejercer el derecho de supresión nunca lo alcanzaría.
 */
@ApplicationService
class ListGroupActivityQuery(
    private val reader: ClubHealthReader,
) {
    @Transactional(readOnly = true)
    fun execute(actor: Principal): Either<SeguimientoError, List<GroupActivity>> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.CLUB_HEALTH, Action.LIST)) {
                SeguimientoError.Forbidden
            }
            reader.findLastActivityByGroup(ClubId.of(actor.clubId))
        }
}
