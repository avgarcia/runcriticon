package com.runcriticon.clubtaxonomia.application.usecases.students

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ClubTaxonomiaAccessAuditor
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentDirectory
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.application.usecases.INJURY_TAG_RESOURCE
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.person.StudentSummary
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.taxonomy.injuryAxis
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.rgpd.AccessType
import com.runcriticon.shared.rgpd.AuditAccess
import com.runcriticon.shared.rgpd.AuditSubjects
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Alumnos del club con su clasificación, filtrados en AND por [tagValueIds]. El admin y el entrenador.
 *
 * Ambos ven hoy **todos** los alumnos del club, no un subconjunto: la relación entrenador↔alumno todavía no existe, así
 * que no hay nada por lo que filtrar. Cuando exista, este es el sitio donde acotarlo — mismo hueco documentado que
 * `ListGroupsQuery`.
 *
 * `@AuditAccess` (ADR-0009 D15, que cita las lesiones entre lo auditable): la clasificación incluye el tag
 * `estado = lesión` ([com.runcriticon.clubtaxonomia.domain.taxonomy.InjuryAxis]), un dato de salud. Se audita a cada
 * alumno que sale con ese tag, no al listado entero — filtrar por él (`?tagValueId=<lesión>`) devuelve la lista
 * nominal de lesionados del club, y es justo ese acceso el que tiene que quedar registrado.
 */
@ApplicationService
class ListStudentsQuery(
    private val studentDirectory: StudentDirectory,
    private val taxonomyRepository: TaxonomyRepository,
    private val auditor: ClubTaxonomiaAccessAuditor,
) {
    /** Los alumnos del listado; [auditSubjectIds] son los que llevan el tag de lesión. */
    data class Result(
        val students: List<StudentSummary>,
        private val injuryValueId: TagValueId?,
    ) : AuditSubjects {
        override fun auditSubjectIds(): Set<UUID> =
            if (injuryValueId == null) {
                emptySet()
            } else {
                students.filter { injuryValueId in it.tagValueIds }.mapTo(mutableSetOf()) { it.id.value }
            }
    }

    @AuditAccess(type = AccessType.SALUD, resource = INJURY_TAG_RESOURCE)
    @Transactional
    fun execute(
        actor: Principal,
        tagValueIds: List<UUID>,
    ): Either<ClubTaxonomiaError, Result> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.STUDENT, Action.LIST)) {
                auditor.denegado(actor, Resource.STUDENT, Action.LIST)
                ClubTaxonomiaError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val required = tagValueIds.mapTo(linkedSetOf()) { TagValueId.of(it) }
            Result(
                students = studentDirectory.listByClub(clubId, required),
                injuryValueId = taxonomyRepository.findByClub(clubId).injuryAxis()?.injuryValueId,
            )
        }
}
