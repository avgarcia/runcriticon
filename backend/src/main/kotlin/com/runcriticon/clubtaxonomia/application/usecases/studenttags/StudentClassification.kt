package com.runcriticon.clubtaxonomia.application.usecases.studenttags

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentLookup
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentTagRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.studenttags.StudentTags
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.stereotype.Component

/**
 * Fontanería común de los cuatro casos de uso de clasificación: comprobar que el alumno es del club antes de tocar
 * nada, y componer la clasificación resultante para devolverla.
 *
 * Es un colaborador y no una clase base, por el mismo motivo que la fontanería de la taxonomía: la comprobación de la
 * matriz de autorización tiene que quedar escrita en cada caso de uso —ArchUnit exige que el acceso esté en el
 * bytecode de la propia clase, y la regla busca sobre todo que se *vea* al leerlo—. Lo que aquí se centraliza es lo
 * que no debe variar entre operaciones: que el club sale del principal y que el alumno se valida antes de escribir.
 */
@Component
class StudentClassification(
    private val studentLookup: StudentLookup,
    private val studentTags: StudentTagRepository,
    private val taxonomyRepository: TaxonomyRepository,
) {
    /**
     * Valida el alumno, ejecuta [action] con el contexto ya cargado y devuelve la clasificación resultante.
     *
     * El orden importa: [StudentLookup.isStudent] toma un bloqueo sobre la persona que dura hasta el commit, así que
     * llamarlo lo primero es lo que impide que una supresión simultánea deje asignaciones huérfanas.
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
            action(Context(clubId, studentId, taxonomy, studentTags.findAssignedValueIds(clubId, studentId)))

            StudentTags.of(studentId, taxonomy, studentTags.findAssignedValueIds(clubId, studentId))
        }

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
