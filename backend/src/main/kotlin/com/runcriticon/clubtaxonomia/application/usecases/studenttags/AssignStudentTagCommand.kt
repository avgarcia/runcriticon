package com.runcriticon.clubtaxonomia.application.usecases.studenttags

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentTagRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.studenttags.StudentTags
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Añade un valor a la clasificación que el alumno ya tiene, sin tocar el resto. Es la operación de la acción suelta
 * —añadir una etiqueta desde la ficha— frente al guardado completo del formulario.
 *
 * Idempotente: asignar un valor que el alumno ya llevaba no cambia nada ni da error. Esa comprobación va **dentro** de
 * la validación de asignabilidad, de modo que reasignar un valor archivado que ya tenía tampoco falla.
 *
 * Un alumno puede acabar con varios valores del mismo eje: es N-M de verdad, no un valor por eje.
 */
@ApplicationService
class AssignStudentTagCommand(
    private val classification: StudentClassification,
    private val studentTags: StudentTagRepository,
) {
    @Transactional
    fun execute(
        actor: Principal,
        studentId: UUID,
        valueId: UUID,
    ): Either<ClubTaxonomiaError, StudentTags> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.STUDENT, Action.CLASSIFY)) {
                ClubTaxonomiaError.Forbidden
            }
            val value = TagValueId.of(valueId)

            classification
                .classify(actor, PersonId.of(studentId)) { context ->
                    ensureAssignable(context, value)
                    studentTags.add(context.clubId, context.studentId, value)
                }.bind()
        }
}
