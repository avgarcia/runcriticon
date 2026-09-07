package com.runcriticon.clubtaxonomia.application.usecases.studenttags

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ports.outbound.observability.AuditTrail
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentLookup
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentTagRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.application.usecases.groups.GroupMembershipPublisher
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Fontanería común de los dos casos de uso de clasificación en masa: bloquear y validar a todos los alumnos de una
 * vez, ejecutar la operación y recalcular lo que cambió. Es al lote lo que [StudentClassification] es al alumno
 * suelto — mismo motivo para ser un colaborador y no una clase base: la comprobación de la matriz de autorización
 * tiene que quedar escrita en cada caso de uso, no aquí.
 *
 * Tres invariantes que sostienen la corrección de esta clase, y que cualquier cambio futuro debe conservar:
 *
 * 1. **Un `Left` no revierte nada.** Arrow devuelve el error, no lo lanza, y Spring solo revierte una transacción
 *    ante una excepción. Por eso todas las comprobaciones (`ensure`) van **antes** de la única escritura que hace
 *    [operation]: añadir una comprobación después de escribir rompería el todo-o-nada en silencio, sin que ningún
 *    test lo viera fallar.
 * 2. **Un evento por grupo afectado, publicado al final.** [com.runcriticon.clubtaxonomia.api.events.MembresiaDeGrupoCambiada]
 *    lleva el snapshot completo del grupo, y [GroupMembershipPublisher] lo recalcula en el momento de publicar.
 *    Publicar dentro del bucle por alumno haría que los primeros eventos vieran una membresía a medio aplicar. Por
 *    eso se acumula la unión de las diferencias de todos los alumnos y solo al final se pregunta una vez por los
 *    grupos afectados y se publica una vez por grupo.
 * 3. **Un asiento de auditoría por alumno, no uno por operación.** [AuditTrail.anonymize] anonimiza por persona: un
 *    asiento con cincuenta sujetos dentro no se podría anonimizar cuando uno solo de ellos ejerciera su derecho de
 *    supresión.
 */
@Component
class BulkStudentClassification(
    private val studentLookup: StudentLookup,
    private val studentTags: StudentTagRepository,
    private val taxonomyRepository: TaxonomyRepository,
    private val groupRepository: GroupRepository,
    private val groupMembershipPublisher: GroupMembershipPublisher,
    private val auditTrail: AuditTrail,
) {
    /**
     * Valida y bloquea a todos los [studentIds], ejecuta [operation] una sola vez con el contexto ya cargado y
     * devuelve cuántos alumnos cambiaron de clasificación de verdad.
     *
     * Los ids repetidos se colapsan **antes** de comprobar el conteo de bloqueados: sin esto, `[a, a, b]` con solo
     * `a` siendo alumno válido cuadraría `lockStudents(...) == studentIds.size` y colaría a `b`.
     */
    fun classifyAll(
        actor: Principal,
        studentIds: List<UUID>,
        operation: Raise<ClubTaxonomiaError>.(Context) -> Unit,
    ): Either<ClubTaxonomiaError, Int> =
        either {
            val clubId = ClubId.of(actor.clubId)
            val students = studentIds.mapTo(sortedSetOf(compareBy(PersonId::value))) { PersonId.of(it) }
            ensure(students.isNotEmpty()) {
                ClubTaxonomiaError.InvalidInput(field = "alumnos", reason = "empty")
            }
            ensure(studentLookup.lockStudents(clubId, students) == students.size) {
                ClubTaxonomiaError.StudentNotFound
            }

            val taxonomy = taxonomyRepository.findByClub(clubId)
            val before = assignedByStudent(clubId, students)
            operation(Context(clubId, students, taxonomy, before))
            val after = assignedByStudent(clubId, students)

            var updated = 0
            val changedTotal = mutableSetOf<TagValueId>()
            students.forEach { studentId ->
                val antes = before.getValue(studentId)
                val despues = after.getValue(studentId)
                val changed = (antes - despues) union (despues - antes)
                if (changed.isEmpty()) return@forEach
                updated++
                changedTotal += changed
                auditTrail.record(clubId, tagsAuditEntry(actor, studentId, antes, despues))
            }

            if (changedTotal.isNotEmpty()) {
                val affectedGroups = groupRepository.findGroupIdsByAnyRequiredTagValue(clubId, changedTotal)
                groupMembershipPublisher.publishFor(clubId, actor.userId, affectedGroups)
            }

            updated
        }

    /** Rellena con el conjunto vacío a los alumnos sin ninguna asignación, que el puerto no devuelve. */
    private fun assignedByStudent(
        clubId: ClubId,
        students: Set<PersonId>,
    ): Map<PersonId, Set<TagValueId>> {
        val rows = studentTags.findAssignedValueIdsByStudent(clubId, students)
        return students.associateWith { rows[it] ?: emptySet() }
    }

    /**
     * Estado ya cargado que necesitan las operaciones para decidir: la taxonomía del club y lo que cada alumno lleva.
     */
    data class Context(
        val clubId: ClubId,
        val students: Set<PersonId>,
        val taxonomy: Taxonomy,
        val assigned: Map<PersonId, Set<TagValueId>>,
    )
}

/**
 * Paridad exacta con [ensureAssignable] para un alumno suelto: el valor tiene que existir en la taxonomía, y si
 * **alguno** de los seleccionados no lo llevaba ya, tiene que seguir siendo asignable.
 *
 * Se recorre `context.students` y no `context.assigned.values`: un alumno sin ninguna asignación existe en
 * `students` pero un recorrido de los valores del mapa lo saltaría, y entonces un `all` sobre esos valores daría
 * cierto con que un solo alumno lo tuviera ya — colando un valor archivado a los otros cuarenta y nueve.
 */
internal fun Raise<ClubTaxonomiaError>.ensureAssignableForAll(
    context: BulkStudentClassification.Context,
    valueId: TagValueId,
) {
    ensure(context.taxonomy.findValue(valueId) != null) { ClubTaxonomiaError.TagValueNotFound }
    if (context.students.all { valueId in context.assigned.getValue(it) }) return
    ensure(context.taxonomy.assignableValues().any { it.id == valueId }) {
        ClubTaxonomiaError.Conflict("tag_value_not_assignable")
    }
}
