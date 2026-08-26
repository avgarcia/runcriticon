package com.runcriticon.identidad.application.usecases.consent

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.api.events.ConsentimientoRevocado
import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.persistence.ConsentRepository
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.consent.Consent
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.observability.OpenTelemetryHelper
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * Revocación de consentimiento por el propio alumno, desde `/me/consentimiento` (ADR-0014 D18,
 * LAL-128). Consecuencia real, no solo administrativa: el módulo `seguimiento` consume
 * [ConsentimientoRevocado] y deja de aceptar nuevos reportes de sesión de este alumno hasta que vuelva
 * a conceder — el frontend debe avisar de esto antes de confirmar la acción.
 */
@ApplicationService
class RevokeConsentCommand(
    private val consentRepository: ConsentRepository,
    private val auditTrail: AuditTrail,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
) {
    @Transactional
    fun execute(actor: Principal): Either<IdentidadError, Consent> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.CONSENT, Action.REVOKE)) {
                IdentidadError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val userId = UserId.of(actor.userId)
            val latest = consentRepository.findLatestByUserId(clubId, userId)
            ensureNotNull(latest) { IdentidadError.Conflict("no hay ningún consentimiento que revocar") }
            ensure(latest.isActive()) { IdentidadError.Conflict("el consentimiento ya está revocado") }

            val now = Instant.now(clock)
            val revoked = latest.revoke(now)
            consentRepository.save(revoked)
            auditTrail.record(
                AuditEntry(
                    type = AuditEventType.CONSENTIMIENTO_REVOCADO,
                    actorId = userId.value,
                    subjectId = userId.value,
                    occurredAt = now,
                ),
            )
            eventPublisher.publishEvent(
                ConsentimientoRevocado(
                    eventId = UuidCreator.getTimeOrderedEpoch(),
                    aggregateId = userId.value,
                    occurredAt = now,
                    clubId = clubId.value,
                    actorId = userId.value,
                    traceparent = OpenTelemetryHelper.actualTraceparent(),
                ),
            )
            revoked
        }
}
