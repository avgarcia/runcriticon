package com.runcriticon.clubtaxonomia.application.usecases.studenttags

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ClubTaxonomiaAccessAuditor
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.application.usecases.INJURY_TAG_RESOURCE
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.studenttags.StudentTags
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
 * Devuelve la clasificación actual de un alumno. La consultan ADMIN y ENTRENADOR, los mismos que pueden cambiarla.
 *
 * Puede incluir valores archivados: son asignaciones que siguen vigentes aunque el valor ya no se ofrezca para
 * nuevas.
 *
 * `@AuditAccess` (ADR-0009 D15): si el alumno lleva el tag `estado = lesión`, esta lectura expone un dato de salud
 * suyo y queda registrada; si no, no hay nada que auditar.
 */
@ApplicationService
class ListStudentTagsQuery(
    private val classification: StudentClassification,
    private val taxonomyRepository: TaxonomyRepository,
    private val auditor: ClubTaxonomiaAccessAuditor,
) {
    /** La clasificación del alumno; [auditSubjectIds] es el propio alumno solo si lleva el tag de lesión. */
    data class Result(
        val tags: StudentTags,
        private val showsInjury: Boolean,
    ) : AuditSubjects {
        override fun auditSubjectIds(): Set<UUID> = if (showsInjury) setOf(tags.studentId.value) else emptySet()
    }

    @AuditAccess(type = AccessType.SALUD, resource = INJURY_TAG_RESOURCE)
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
            val injuryValueId = taxonomyRepository.findByClub(ClubId.of(actor.clubId)).injuryAxis()?.injuryValueId
            Result(tags, showsInjury = tags.assigned.any { it.value.id == injuryValueId })
        }
}
