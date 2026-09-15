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
 * Asigna el mismo valor a varios alumnos a la vez, en una sola operación transaccional: o se aplica a todos o a
 * ninguno. Versión en masa de [AssignStudentTagCommand] — comparte con ella la regla de asignabilidad
 * ([ensureAssignableForAll]), pero no la fontanería, porque validar y bloquear alumno a alumno sería
 * cincuenta comprobaciones donde basta una.
 *
 * Idempotente por alumno: quien ya tenía el valor no cuenta como actualizado. El recuento devuelto son los alumnos
 * que cambiaron de verdad, no los enviados.
 */
@ApplicationService
class AssignStudentTagInBulkCommand(
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
                    ensureAssignableForAll(context, value)
                    studentTags.addToAll(context.clubId, context.students, value)
                }.bind()
        }
}
