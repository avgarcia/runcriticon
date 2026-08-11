package com.runcriticon.clubtaxonomia.application.usecases.students

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentDirectory
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.person.StudentSummary
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Alumnos del club con su clasificación, filtrados en AND por [tagValueIds]. El admin y el entrenador.
 *
 * Ambos ven hoy **todos** los alumnos del club, no un subconjunto: la relación entrenador↔alumno todavía no existe, así
 * que no hay nada por lo que filtrar. Cuando exista, este es el sitio donde acotarlo — mismo hueco documentado que
 * `ListGroupsQuery`.
 */
@ApplicationService
class ListStudentsQuery(
    private val studentDirectory: StudentDirectory,
) {
    @Transactional(readOnly = true)
    fun execute(
        actor: Principal,
        tagValueIds: List<UUID>,
    ): Either<ClubTaxonomiaError, List<StudentSummary>> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.STUDENT, Action.LIST)) {
                ClubTaxonomiaError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val required = tagValueIds.mapTo(linkedSetOf()) { TagValueId.of(it) }
            studentDirectory.listByClub(clubId, required)
        }
}
