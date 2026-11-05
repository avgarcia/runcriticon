package com.runcriticon.identidad.application.listeners

import com.runcriticon.identidad.application.ports.inbound.DeletionGraceExpired
import com.runcriticon.identidad.application.ports.outbound.observability.RetentionMetrics
import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.application.usecases.account.UserEraser
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.autorizacion.ListenerPrincipalScope
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.events.ProcessedEventTracker
import com.runcriticon.shared.observability.MdcRestorerForEvents
import com.runcriticon.shared.tenancy.ClubId
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Suprime una cuenta cuya baja venció (ADR-0017 D9), con la misma supresión que la inmediata ([UserEraser]). Corre en
 * una transacción propia por cuenta (`@ApplicationModuleListener`): si falla, la cuenta sigue en `BAJA` y el job la
 * vuelve a enviar al día siguiente, sin arrastrar a las demás.
 *
 * Bloquea la fila y vuelve a comprobar que la cuenta sigue en `BAJA` con la gracia vencida, saltándola si otra
 * transacción la tiene bloqueada. Así, si dos instancias ejecutan el job a la vez y envían la misma cuenta dos veces,
 * la segunda entrega no la encuentra; y si el admin canceló la baja o la cuenta ya se suprimió, no hace nada.
 *
 * El principal es la propia cuenta, como hace `HealthConsentListener` con el alumno: los repositorios solo comprueban
 * que el club coincide, y la cuenta es lo único que el evento identifica. La supresión sale con `actorId` nulo porque
 * la provoca el vencimiento del plazo, no una persona; quién pidió la baja queda en el asiento `BAJA_MARCADA`.
 */
@Component
class DeletionGraceExpiredListener(
    private val userRepository: UserRepository,
    private val userEraser: UserEraser,
    private val principalScope: ListenerPrincipalScope,
    private val metrics: RetentionMetrics,
    private val mdcRestorer: MdcRestorerForEvents,
    // Qualifier por el literal, no por la constante del adaptador: importarla haría que esta clase de
    // `application` dependiera de `infrastructure`, la dirección prohibida.
    @Qualifier("identidadProcessedEventTracker")
    private val processedEvents: ProcessedEventTracker,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ApplicationModuleListener
    fun on(event: DeletionGraceExpired) {
        mdcRestorer.restore(
            module = "identidad",
            traceparent = event.traceparent,
            clubId = event.clubId,
            actorId = null,
        )
        try {
            if (!processedEvents.markIfNew(LISTENER, event.eventId)) {
                log.debug("Evento {} ya procesado por {}; se descarta", event.eventId, LISTENER)
                return
            }
            val principal = Principal(userId = event.userId, clubId = event.clubId, role = event.role)
            principalScope.runAs(principal) { erase(event) }
        } finally {
            mdcRestorer.clear()
        }
    }

    private fun erase(event: DeletionGraceExpired) {
        val now = Instant.now()
        val target =
            userRepository.lockExpiredDeletion(
                ClubId.of(event.clubId),
                UserId.of(event.userId),
                cutoff = now.minus(User.DELETION_GRACE),
            )
        // Los logs citan el evento, no la cuenta: el id de la persona suprimida no debe sobrevivirle en los logs
        // operativos, que el MDC ya protege con `user_id_hash`.
        if (target == null) {
            log.info("Evento {}: la cuenta ya no está en baja vencida o la suprime otra transacción", event.eventId)
            return
        }
        userEraser.erase(target, actorId = null, now = now)
        metrics.purged(TABLE_USUARIO, 1)
        log.info("Retención identidad: evento {}, cuenta suprimida al vencer la gracia de su baja", event.eventId)
    }

    private companion object {
        const val LISTENER = "DeletionGraceExpiredListener"
        const val TABLE_USUARIO = "usuario"
    }
}
