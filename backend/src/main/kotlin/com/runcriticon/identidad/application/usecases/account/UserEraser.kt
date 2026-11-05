package com.runcriticon.identidad.application.usecases.account

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.api.events.AdminEliminado
import com.runcriticon.identidad.api.events.AlumnoEliminado
import com.runcriticon.identidad.api.events.EntrenadorEliminado
import com.runcriticon.identidad.application.ports.outbound.observability.AuditTrail
import com.runcriticon.identidad.application.ports.outbound.persistence.ConsentRepository
import com.runcriticon.identidad.application.ports.outbound.persistence.InvitationRepository
import com.runcriticon.identidad.application.ports.outbound.persistence.MagicLinkRepository
import com.runcriticon.identidad.application.ports.outbound.persistence.PasswordHistory
import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.domain.audit.AuditEntry
import com.runcriticon.identidad.domain.audit.AuditEventType
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.events.IntegrationEvent
import com.runcriticon.shared.observability.OpenTelemetryHelper
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * Supresión de una cuenta (ADR-0014 D14): borra físicamente al usuario y los datos personales que cuelgan de él,
 * anonimiza su auditoría local y publica la baja para que el resto de módulos borre lo suyo. La comparten la supresión
 * inmediata ([DeleteUserCommand]) y la de las bajas con la gracia vencida (ADR-0017 D9), así que las dos dejan el mismo
 * rastro.
 *
 * No autoriza ni comprueba el estado de la cuenta: eso lo hace quien la llama. Tampoco revoca sesiones: la supresión
 * inmediata las revoca antes, y una cuenta en baja ya las perdió al marcarse. Debe ejecutarse dentro de la transacción
 * de quien la llama, incluida la publicación al outbox: si el borrado hace rollback, el evento no sale.
 *
 * Rastro que queda: el asiento [AuditEventType.CUENTA_ELIMINADA], que se registra ya sin `subjectId` — el enlace
 * entre esta baja y la persona suprimida vive en el runbook de atención de la solicitud, no en la tabla. Antes de
 * escribirlo, se anonimizan todos los asientos previos que mencionaban a la persona (como actor o como sujeto) y los
 * que solo quedaron ligados a su email por el `email_hash` de un evento de rate-limiting. El barrido va primero
 * porque `auditTrail.record` persiste vía JPA sin flush inmediato: anonimizar después no vería la fila recién
 * escrita.
 */
@Component
class UserEraser(
    private val userRepository: UserRepository,
    private val invitationRepository: InvitationRepository,
    private val magicLinkRepository: MagicLinkRepository,
    private val passwordHistory: PasswordHistory,
    private val consentRepository: ConsentRepository,
    private val auditTrail: AuditTrail,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /**
     * @param actorId quien provoca la supresión: el admin que la ejecuta, o `null` cuando la provoca el vencimiento de
     *   la gracia y no una persona (ADR-0017 D9).
     */
    fun erase(
        target: User,
        actorId: UUID?,
        now: Instant,
    ) {
        eraseIn(target.clubId, target.id)
        auditTrail.anonymize(target.id.value, target.email)
        auditTrail.record(
            AuditEntry(
                type = AuditEventType.CUENTA_ELIMINADA,
                actorId = actorId,
                subjectId = null,
                occurredAt = now,
            ),
        )
        eventPublisher.publishEvent(deletedEvent(target, actorId, now))
    }

    /**
     * Orden impuesto por las claves ajenas a `usuario`, que no tienen `ON DELETE CASCADE`: primero todo lo que apunta
     * al usuario, después el usuario.
     *
     * Las invitaciones que el usuario emitió a otras personas no se borran —siguen siendo de ellas—, pero sí pierden
     * el `invitado_por` que lo identificaba. Esa columna no tiene clave ajena, así que el orden no la afecta; va aquí
     * porque, como el resto, es dato personal del suprimido que no puede sobrevivirle.
     */
    private fun eraseIn(
        clubId: ClubId,
        userId: UserId,
    ) {
        magicLinkRepository.deleteByUserId(clubId, userId)
        invitationRepository.deleteByUserId(clubId, userId)
        invitationRepository.clearInviter(clubId, userId)
        passwordHistory.deleteByUserId(clubId, userId)
        consentRepository.deleteByUserId(clubId, userId)
        userRepository.deleteById(clubId, userId)
    }

    /**
     * La baja según el rol. El ADMIN **sí genera evento** pese a no existir como persona proyectada en ningún módulo
     * (no hay `AdminInvitado`, se siembra directo en `identidad`): aunque no dispara borrado físico en ningún
     * consumidor, es lo único que permite a `club_taxonomia`/`auditoria` anonimizar el `actor_id` de las acciones que
     * el ADMIN realizó — sin evento, esos módulos no tienen forma de enterarse de la baja.
     *
     * `actorId` es quien provoca la supresión, no la persona suprimida. El sujeto viaja en `aggregateId`, que es lo que
     * el consumidor necesita para saber a quién borrar.
     */
    private fun deletedEvent(
        deleted: User,
        actorId: UUID?,
        occurredAt: Instant,
    ): IntegrationEvent {
        val eventId = UuidCreator.getTimeOrderedEpoch()
        val aggregateId = deleted.id.value
        val clubId = deleted.clubId.value
        val traceparent = OpenTelemetryHelper.actualTraceparent()
        return when (deleted.role) {
            Role.ALUMNO ->
                AlumnoEliminado(
                    eventId = eventId,
                    aggregateId = aggregateId,
                    occurredAt = occurredAt,
                    clubId = clubId,
                    actorId = actorId,
                    traceparent = traceparent,
                )
            Role.ENTRENADOR ->
                EntrenadorEliminado(
                    eventId = eventId,
                    aggregateId = aggregateId,
                    occurredAt = occurredAt,
                    clubId = clubId,
                    actorId = actorId,
                    traceparent = traceparent,
                )
            Role.ADMIN ->
                AdminEliminado(
                    eventId = eventId,
                    aggregateId = aggregateId,
                    occurredAt = occurredAt,
                    clubId = clubId,
                    actorId = actorId,
                    traceparent = traceparent,
                )
        }
    }
}
