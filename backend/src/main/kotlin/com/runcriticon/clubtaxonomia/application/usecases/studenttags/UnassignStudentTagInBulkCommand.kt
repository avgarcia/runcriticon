package com.runcriticon.clubtaxonomia.application.usecases.studenttags

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentTagRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Quita el mismo valor a varios alumnos a la vez, en una sola operación transaccional. Versión en masa de
 * [UnassignStudentTagCommand]: como ella, no valida la taxonomía — un valor archivado, o incluso uno que ya no
 * exista, siempre se puede quitar. Lo contrario dejaría clasificaciones imposibles de limpiar.
 *
 * Idempotente por alumno: quien no tenía el valor no hace fallar nada, sencillamente no cuenta como actualizado.
 */
@ApplicationService
class UnassignStudentTagInBulkCommand(
    private val classification: BulkStudentClassification,
    private val studentTags: StudentTagRepository,
) {
    @Transactional
    fun execute(
        actor: Principal,
        studentIds: List<UUID>,
        valueId: UUID,
    ): Either<ClubTaxonomiaError, Int> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.STUDENT, Action.CLASSIFY)) {
                ClubTaxonomiaError.Forbidden
            }
            val value = TagValueId.of(valueId)

            classification
                .classifyAll(actor, studentIds) { context ->
                    studentTags.removeFromAll(context.clubId, context.students, value)
                }.bind()
        }
}
