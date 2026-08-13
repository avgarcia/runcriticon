package com.runcriticon.clubtaxonomia.application.usecases.groups

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.api.events.AlumnoAsignadoAGrupo
import com.runcriticon.clubtaxonomia.api.events.AlumnoEliminadoDeGrupo
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentLookup
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.GroupDetail
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.observability.OpenTelemetryHelper
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Mete o saca a un alumno de un grupo sin tocar sus tags: la excepción manual del modelo de grupos. El admin y el
 * entrenador, que son quienes arman los grupos con los que trabajan.
 *
 * Idempotente y sin borrado previo para cambiar de sentido: escribir la excepción contraria la sobrescribe. La
 * excepción **prevalece sobre los cambios posteriores de la clasificación** del alumno, porque no se recalcula nunca:
 * la resolución la vuelve a aplicar tal cual en cada lectura.
 *
 * **El orden de las guardas no es casual.** Primero el grupo, porque es el recurso de la ruta padre y con ambos
 * inválidos manda su 404. Y la comprobación del alumno se hace con [StudentLookup.isStudent] —no con un `SELECT`
 * cualquiera— porque toma un bloqueo sobre la persona que dura hasta el commit: sin él, una supresión simultánea
 * borraría sus datos y esta escritura los repondría en forma de excepción huérfana. Además evita una fila fantasma:
 * una excepción sobre un entrenador o sobre un id inventado se guardaría y sería invisible en toda lectura, porque el
 * detalle solo devuelve alumnos del club.
 *
 * **Publica** [AlumnoAsignadoAGrupo] (`included = true`) o [AlumnoEliminadoDeGrupo] (`included = false`), en la misma
 * transacción (LAL-94). [ClearGroupMembershipOverrideCommand] **no publica nada**: quitar la excepción no determina
 * por sí solo si el alumno queda dentro o fuera del grupo — depende del filtro de tags vigente, que este módulo no
 * recalcula para emitir el evento (recorte documentado en el `README.md` del módulo).
 */
@ApplicationService
class OverrideGroupMembershipCommand(
    private val groupRepository: GroupRepository,
    private val studentLookup: StudentLookup,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun execute(
        actor: Principal,
        groupId: UUID,
        studentId: UUID,
        included: Boolean,
    ): Either<ClubTaxonomiaError, GroupDetail> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.GROUP, Action.UPDATE)) {
                ClubTaxonomiaError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val group = GroupId.of(groupId)
            val student = PersonId.of(studentId)

            ensureGroupOfClub(groupRepository, clubId, group)
            ensure(studentLookup.isStudent(clubId, student)) { ClubTaxonomiaError.StudentNotFound }

            groupRepository.upsertOverride(clubId, group, student, included)
            publishMembershipEvent(actor, group, student, included)

            // Se devuelve el detalle ya recalculado, en la misma transacción: la pantalla necesita el nuevo recuento y
            // el nuevo origen de cada miembro, y pedirlo aparte daría una lectura fuera de esta transacción.
            groupRepository.findDetail(clubId, group) ?: raise(ClubTaxonomiaError.GroupNotFound)
        }

    private fun publishMembershipEvent(
        actor: Principal,
        group: GroupId,
        student: PersonId,
        included: Boolean,
    ) {
        val eventId = UuidCreator.getTimeOrderedEpoch()
        val occurredAt = Instant.now()
        val traceparent = OpenTelemetryHelper.actualTraceparent()
        val event =
            if (included) {
                AlumnoAsignadoAGrupo(
                    eventId = eventId,
                    aggregateId = student.value,
                    occurredAt = occurredAt,
                    clubId = actor.clubId,
                    actorId = actor.userId,
                    traceparent = traceparent,
                    groupId = group.value,
                )
            } else {
                AlumnoEliminadoDeGrupo(
                    eventId = eventId,
                    aggregateId = student.value,
                    occurredAt = occurredAt,
                    clubId = actor.clubId,
                    actorId = actor.userId,
                    traceparent = traceparent,
                    groupId = group.value,
                )
            }
        eventPublisher.publishEvent(event)
    }
}
