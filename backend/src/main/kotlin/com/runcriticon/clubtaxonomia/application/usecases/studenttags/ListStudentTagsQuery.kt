package com.runcriticon.clubtaxonomia.application.usecases.studenttags

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.studenttags.StudentTags
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Devuelve la clasificación actual de un alumno. La consultan ADMIN y ENTRENADOR, los mismos que pueden cambiarla.
 *
 * Puede incluir valores archivados: son asignaciones que siguen vigentes aunque el valor ya no se ofrezca para
 * nuevas.
 */
@ApplicationService
class ListStudentTagsQuery(
    private val classification: StudentClassification,
) {
    @Transactional(readOnly = true)
    fun execute(
        actor: Principal,
        studentId: UUID,
    ): Either<ClubTaxonomiaError, StudentTags> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.STUDENT, Action.CLASSIFY)) {
                ClubTaxonomiaError.Forbidden
            }
            classification.classify(actor, PersonId.of(studentId)) { }.bind()
        }
}
