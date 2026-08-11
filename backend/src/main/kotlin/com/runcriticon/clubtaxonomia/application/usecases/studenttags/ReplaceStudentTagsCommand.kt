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
 * Deja al alumno exactamente con los valores indicados: lo que no aparezca se desasigna. Es la operación que respalda
 * el formulario de clasificación, donde el usuario ve todos sus chips a la vez y guarda una sola vez.
 *
 * Idempotente: repetir la misma lista no cambia nada y devuelve lo mismo. Una lista vacía borra toda la clasificación,
 * que es lo que significa guardar el formulario sin ninguna etiqueta.
 *
 * Los ids repetidos se colapsan — llega un conjunto, no una lista.
 *
 * **Deuda documentada (LAL-87, ADR-0002 D5):** la pertenencia a un grupo vivo se actualiza sola porque es una query en
 * vivo sobre `alumno_tag` (D3), pero la no-retroactividad sobre un plan **ya publicado** con snapshot de membresía
 * congelada la tiene que sostener el módulo Planificación, que hoy no existe (sin agregado de plan, sin publicación,
 * sin snapshot). Hasta que exista, este comando no tiene ningún plan publicado que respetar ni que romper.
 */
@ApplicationService
class ReplaceStudentTagsCommand(
    private val classification: StudentClassification,
    private val studentTags: StudentTagRepository,
) {
    @Transactional
    fun execute(
        actor: Principal,
        studentId: UUID,
        valueIds: List<UUID>,
    ): Either<ClubTaxonomiaError, StudentTags> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.STUDENT, Action.CLASSIFY)) {
                ClubTaxonomiaError.Forbidden
            }
            val requested = valueIds.mapTo(mutableSetOf()) { TagValueId.of(it) }

            classification
                .classify(actor, PersonId.of(studentId)) { context ->
                    requested.forEach { ensureAssignable(context, it) }
                    studentTags.replace(context.clubId, context.studentId, requested)
                }.bind()
        }
}
