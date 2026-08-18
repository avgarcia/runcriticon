package com.runcriticon.clubtaxonomia.application.usecases.studenttags

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentTagRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Quita un valor de la clasificación de un alumno.
 *
 * **No valida la taxonomía en absoluto**, ni siquiera que el valor exista: un valor archivado —o uno cuyo eje se
 * archivó, o uno que ya no está— siempre debe poder quitarse. Lo contrario dejaría clasificaciones imposibles de
 * limpiar, justo en el escenario en que hay más ganas de limpiarlas.
 *
 * Idempotente: quitar algo que el alumno no tenía no es un error, porque el estado final es el que se pedía.
 *
 * Misma nota que [ReplaceStudentTagsCommand] sobre la no-retroactividad: la sostiene el módulo Planificación con
 * su propio snapshot congelado al publicar, este comando no tiene que saber nada de planes.
 */
@ApplicationService
class UnassignStudentTagCommand(
    private val classification: StudentClassification,
    private val studentTags: StudentTagRepository,
) {
    @Transactional
    fun execute(
        actor: Principal,
        studentId: UUID,
        valueId: UUID,
    ): Either<ClubTaxonomiaError, Unit> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.STUDENT, Action.CLASSIFY)) {
                ClubTaxonomiaError.Forbidden
            }
            val value = TagValueId.of(valueId)

            classification
                .classify(actor, PersonId.of(studentId)) { context ->
                    studentTags.remove(context.clubId, context.studentId, value)
                }.bind()
        }
}
