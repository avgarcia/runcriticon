package com.runcriticon.clubtaxonomia.application.usecases.students

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ClubTaxonomiaAccessAuditor
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentDirectory
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.application.usecases.HEALTH_TAG_RESOURCE
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.person.StudentSummary
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.taxonomy.healthDataValueIds
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
 * `@AuditAccess` (ADR-0009 D15, que cita las lesiones entre lo auditable): la clasificación puede incluir valores
 * marcados como dato de salud (`TagValue.healthData`: `lesión`, `post-parto`). Se audita a cada alumno que sale con
 * alguno, no al listado entero — filtrar por uno (`?tagValueId=<lesión>`) devuelve la lista nominal de lesionados del
 * club, y es justo ese acceso el que tiene que quedar registrado.
 */
@ApplicationService
class ListStudentsQuery(
    private val studentDirectory: StudentDirectory,
    private val taxonomyRepository: TaxonomyRepository,
    private val auditor: ClubTaxonomiaAccessAuditor,
) {
    /** Los alumnos del listado; [auditSubjectIds] son los que llevan algún valor de salud. */
    data class Result(
        val students: List<StudentSummary>,
        private val healthValueIds: Set<TagValueId>,
    ) : AuditSubjects {
        override fun auditSubjectIds(): Set<UUID> =
            students
                .filter { student -> student.tagValueIds.any { it in healthValueIds } }
                .mapTo(mutableSetOf()) { it.id.value }
    }

    @AuditAccess(type = AccessType.SALUD, resource = HEALTH_TAG_RESOURCE)
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
                healthValueIds = taxonomyRepository.findByClub(clubId).healthDataValueIds(),
            )
        }
}
