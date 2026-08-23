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
import com.runcriticon.clubtaxonomia.domain.audit.AuditEntry
import com.runcriticon.clubtaxonomia.domain.audit.AuditEventType
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.studenttags.StudentTags
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Fontanería común de los cuatro casos de uso de clasificación: comprobar que el alumno es del club antes de tocar
 * nada, componer la clasificación resultante para devolverla y **recalcular la membresía de los grupos afectados**
 * por el cambio (LAL-25, prerrequisito de publicar).
 *
 * Es un colaborador y no una clase base, por el mismo motivo que la fontanería de la taxonomía: la comprobación de la
 * matriz de autorización tiene que quedar escrita en cada caso de uso —ArchUnit exige que el acceso esté en el
 * bytecode de la propia clase, y la regla busca sobre todo que se *vea* al leerlo—. Lo que aquí se centraliza es lo
 * que no debe variar entre operaciones: que el club sale del principal, que el alumno se valida antes de escribir, y
 * que un cambio de tags recalcula los grupos que le tocan.
 */
@Component
class StudentClassification(
    private val studentLookup: StudentLookup,
    private val studentTags: StudentTagRepository,
    private val taxonomyRepository: TaxonomyRepository,
    private val groupRepository: GroupRepository,
    private val groupMembershipPublisher: GroupMembershipPublisher,
    private val auditTrail: AuditTrail,
) {
    /**
     * Valida el alumno, ejecuta [action] con el contexto ya cargado y devuelve la clasificación resultante.
     *
     * El orden importa: [StudentLookup.isStudent] toma un bloqueo sobre la persona que dura hasta el commit, así que
     * llamarlo lo primero es lo que impide que una supresión simultánea deje asignaciones huérfanas.
     *
     * **Recálculo de membresía**: la pertenencia a un grupo por tags cambia solo si el valor tocado está en el
     * filtro de ese grupo -- `Δ` (diferencia simétrica entre lo que el alumno tenía antes de [action] y lo que tiene
     * después) es justo eso, y ya está a mano sin lecturas nuevas: `assigned` es el conjunto previo y
     * [StudentTagRepository.findAssignedValueIds] se llama de todos modos para componer el resultado. Solo se
     * recalculan (y publican) los grupos cuyo filtro toca `Δ`; un grupo cuyo filtro no usa ninguno de esos valores no
     * puede haber cambiado su condición `tags(alumno) ⊇ filtro(grupo)`.
     *
     * **Auditoría (LAL-87 AC3)**: `Δ` no vacío también deja un asiento con `before`/`after` completos, no solo el
     * delta — es lo que pide el AC ("qué tags tenía el alumno antes/después"). Centralizado aquí, no en cada comando,
     * porque es el único punto que ve `Replace`/`Assign`/`Unassign` a la vez con ambos snapshots ya en la mano; una
     * llamada que no cambia nada no genera ruido de auditoría.
     */
    fun classify(
        actor: Principal,
        studentId: PersonId,
        action: Raise<ClubTaxonomiaError>.(Context) -> Unit,
    ): Either<ClubTaxonomiaError, StudentTags> =
        either {
            val clubId = ClubId.of(actor.clubId)
            ensure(studentLookup.isStudent(clubId, studentId)) { ClubTaxonomiaError.StudentNotFound }

            val taxonomy = taxonomyRepository.findByClub(clubId)
            val before = studentTags.findAssignedValueIds(clubId, studentId)
            action(Context(clubId, studentId, taxonomy, before))
            val after = studentTags.findAssignedValueIds(clubId, studentId)

            val changed = (before - after) union (after - before)
            if (changed.isNotEmpty()) {
                val affectedGroups = groupRepository.findGroupIdsByAnyRequiredTagValue(clubId, changed)
                groupMembershipPublisher.publishFor(clubId, actor.userId, affectedGroups)
                auditTrail.record(clubId, auditEntryFor(actor, studentId, before, after))
            }

            StudentTags.of(studentId, taxonomy, after)
        }

    private fun auditEntryFor(
        actor: Principal,
        studentId: PersonId,
        before: Set<TagValueId>,
        after: Set<TagValueId>,
    ) = AuditEntry(
        type = AuditEventType.TAGS_ALUMNO_ACTUALIZADOS,
        actorId = actor.userId,
        subjectId = studentId.value,
        occurredAt = Instant.now(),
        metadata =
            mapOf(
                "antes" to before.map { it.value.toString() },
                "despues" to after.map { it.value.toString() },
            ),
    )

    /** Estado ya cargado que necesitan las operaciones para decidir: la taxonomía del club y lo que el alumno tiene. */
    data class Context(
        val clubId: ClubId,
        val studentId: PersonId,
        val taxonomy: Taxonomy,
        val assigned: Set<TagValueId>,
    )
}

/**
 * Comprueba que [valueId] existe en la taxonomía y, **si el alumno no lo tenía ya**, que sigue siendo asignable.
 *
 * Esa distinción es la regla menos evidente de la clasificación: archivar un valor deja de ofrecerlo para asignaciones
 * nuevas pero conserva las existentes, así que validar la asignabilidad de lo que el alumno ya lleva convertiría su
 * formulario en un error permanente que nadie puede limpiar — leerlo y volver a guardarlo sin tocar nada fallaría.
 */
internal fun Raise<ClubTaxonomiaError>.ensureAssignable(
    context: StudentClassification.Context,
    valueId: TagValueId,
) {
    ensure(context.taxonomy.findValue(valueId) != null) { ClubTaxonomiaError.TagValueNotFound }
    if (valueId in context.assigned) return
    ensure(context.taxonomy.assignableValues().any { it.id == valueId }) {
        ClubTaxonomiaError.Conflict("tag_value_not_assignable")
    }
}
