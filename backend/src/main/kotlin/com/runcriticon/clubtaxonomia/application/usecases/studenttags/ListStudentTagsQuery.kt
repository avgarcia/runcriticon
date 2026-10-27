package com.runcriticon.clubtaxonomia.application.usecases.studenttags

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ClubTaxonomiaAccessAuditor
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.application.usecases.HEALTH_TAG_RESOURCE
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.studenttags.StudentTags
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
 * Devuelve la clasificación actual de un alumno. La consultan ADMIN y ENTRENADOR, los mismos que pueden cambiarla.
 *
 * Puede incluir valores archivados: son asignaciones que siguen vigentes aunque el valor ya no se ofrezca para
 * nuevas.
 *
 * `@AuditAccess` (ADR-0009 D15): si el alumno lleva algún valor marcado como dato de salud (`lesión`, `post-parto`),
 * esta lectura lo expone y queda registrada; si no, no hay nada que auditar.
 */
@ApplicationService
class ListStudentTagsQuery(
    private val classification: StudentClassification,
    private val taxonomyRepository: TaxonomyRepository,
    private val auditor: ClubTaxonomiaAccessAuditor,
) {
    /** La clasificación del alumno; [auditSubjectIds] es el propio alumno solo si lleva algún valor de salud. */
    data class Result(
        val tags: StudentTags,
        private val showsHealthData: Boolean,
    ) : AuditSubjects {
        override fun auditSubjectIds(): Set<UUID> = if (showsHealthData) setOf(tags.studentId.value) else emptySet()
    }

    @AuditAccess(type = AccessType.SALUD, resource = HEALTH_TAG_RESOURCE)
    @Transactional
    fun execute(
        actor: Principal,
        studentId: UUID,
    ): Either<ClubTaxonomiaError, Result> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.STUDENT, Action.CLASSIFY)) {
                auditor.denegado(actor, Resource.STUDENT, Action.CLASSIFY)
                ClubTaxonomiaError.Forbidden
            }
            val tags = classification.classify(actor, PersonId.of(studentId)) { }.bind()
            val healthValueIds = taxonomyRepository.findByClub(ClubId.of(actor.clubId)).healthDataValueIds()
            Result(tags, showsHealthData = tags.assigned.any { it.value.id in healthValueIds })
        }
}
