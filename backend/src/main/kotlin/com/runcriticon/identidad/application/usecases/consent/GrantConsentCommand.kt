package com.runcriticon.identidad.application.usecases.consent

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.api.events.ConsentimientoConcedido
import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.persistence.ConsentRepository
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.consent.Consent
import com.runcriticon.identidad.domain.consent.ConsentText
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
 * Concesión de consentimiento por el propio alumno desde `/me/consentimiento` (LAL-128): cubre a quien
 * activó su cuenta antes de que existiera este mecanismo (queda en `PENDIENTE`) y a quien vuelve a
 * conceder tras revocar. Ver [com.runcriticon.identidad.application.usecases.account.ActivateAccountCommand]
 * para la concesión que ocurre en la propia activación.
 *
 * **Idempotente si ya está vigente**: conceder dos veces no crea una fila redundante ni reemite el
 * evento — devuelve la fila activa tal cual.
 */
@ApplicationService
class GrantConsentCommand(
    private val consentRepository: ConsentRepository,
    private val auditTrail: AuditTrail,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
) {
    @Transactional
    fun execute(
        actor: Principal,
        consentVersion: String?,
        clientIp: String,
        userAgent: String,
    ): Either<IdentidadError, Consent> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.CONSENT, Action.GRANT)) {
                IdentidadError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val userId = UserId.of(actor.userId)
            val existing = consentRepository.findLatestByUserId(clubId, userId)
            if (existing != null && existing.isActive()) {
                existing
            } else {
                ensure(consentVersion == ConsentText.CURRENT_VERSION) { IdentidadError.ConsentTextOutdated }
                grant(userId, clubId, clientIp, userAgent)
            }
        }

    private fun grant(
        userId: UserId,
        clubId: ClubId,
        clientIp: String,
        userAgent: String,
    ): Consent {
        val now = Instant.now(clock)
        val consent =
            Consent.grant(
                userId = userId,
                clubId = clubId,
                textVersion = ConsentText.CURRENT_VERSION,
                ip = clientIp,
                userAgent = userAgent,
                now = now,
            )
        consentRepository.save(consent)
        auditTrail.record(
            AuditEntry(
                type = AuditEventType.CONSENTIMIENTO_CONCEDIDO,
                actorId = userId.value,
                subjectId = userId.value,
                occurredAt = now,
            ),
        )
        eventPublisher.publishEvent(
            ConsentimientoConcedido(
                eventId = UuidCreator.getTimeOrderedEpoch(),
                aggregateId = userId.value,
                occurredAt = now,
                clubId = clubId.value,
                actorId = userId.value,
                traceparent = OpenTelemetryHelper.actualTraceparent(),
                versionTexto = consent.textVersion,
            ),
        )
        return consent
    }
}
