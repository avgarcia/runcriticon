package com.runcriticon.auditoria.application.usecases.events

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.auditoria.application.ports.outbound.persistence.AuditEventFilter
import com.runcriticon.auditoria.application.ports.outbound.persistence.AuditEventRepository
import com.runcriticon.auditoria.domain.AuditEvent
import com.runcriticon.auditoria.domain.AuditoriaError
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId

/**
 * Consulta forense del log de auditoría (ADR-0009 D17): `filtros por userId, clubId, ventana temporal, tipo de
 * evento`. `clubId` sale siempre del principal, nunca de un parámetro del cliente — mismo criterio anti-IDOR que
 * el resto del código.
 */
@ApplicationService
class ListAuditEventsQuery(
    private val repository: AuditEventRepository,
) {
    fun execute(
        actor: Principal,
        filter: AuditEventFilter,
    ): Either<AuditoriaError, List<AuditEvent>> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.AUDIT_EVENT, Action.LIST)) {
                AuditoriaError.Forbidden
            }
            val desde = filter.desde
            val hasta = filter.hasta
            ensure(desde == null || hasta == null || !desde.isAfter(hasta)) {
                AuditoriaError.InvalidInput("desde", "no puede ser posterior a hasta")
            }
            repository.search(ClubId.of(actor.clubId), filter)
        }
}
