package com.runcriticon.clubtaxonomia.application.usecases.groups

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentLookup
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.GroupDetail
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.application.annotations.ApplicationService
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Action
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Resource
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Mete o saca a un alumno de un grupo sin tocar sus tags: la excepción manual del modelo de grupos. El admin y el
 * entrenador, que son quienes arman los grupos con los que trabajan.
 *
 * Idempotente y sin borrado previo para cambiar de sentido: escribir la excepción contraria la sobrescribe. La
 * excepción **prevalece sobre los cambios posteriores de la clasificación** del alumno, porque no se recalcula nunca:
 * la resolución la vuelve a aplicar tal cual en cada lectura.
 *
 * **El orden de las guardas no es casual.** Primero el grupo, porque es el recurso de la ruta padre y con ambos
 * inválidos manda su 404. Y la comprobación del alumno se hace con [StudentLookup.isStudent] —no con un `SELECT`
 * cualquiera— porque toma un bloqueo sobre la persona que dura hasta el commit: sin él, una supresión simultánea
 * borraría sus datos y esta escritura los repondría en forma de excepción huérfana. Además evita una fila fantasma:
 * una excepción sobre un entrenador o sobre un id inventado se guardaría y sería invisible en toda lectura, porque el
 * detalle solo devuelve alumnos del club.
 *
 * **Publica** `MembresiaDeGrupoCambiada` (el snapshot completo del grupo, no un delta) con la membresía que ya
 * calculó [GroupRepository.findDetail] para la respuesta -- sin repetir la consulta. Sustituye a los antiguos
 * `AlumnoAsignadoAGrupo`/`AlumnoEliminadoDeGrupo` (LAL-94, retirados): aquellos solo cubrían esta excepción manual,
 * nunca la pertenencia por tags, así que no podían ser una fuente completa de membresía para nadie que los
 * consumiera. [ClearGroupMembershipOverrideCommand] ahora **sí** publica: con el snapshot completo ya no hace
 * falta saber si el alumno queda dentro o fuera del grupo para decidir qué evento emitir.
 */
@ApplicationService
class OverrideGroupMembershipCommand(
    private val groupRepository: GroupRepository,
    private val studentLookup: StudentLookup,
    private val groupMembershipPublisher: GroupMembershipPublisher,
) {
    @Transactional
    fun execute(
        actor: Principal,
        groupId: UUID,
        studentId: UUID,
        included: Boolean,
    ): Either<ClubTaxonomiaError, GroupDetail> =
        either {
            ensure(AuthorizationMatrix.can(actor.role, Resource.GROUP, Action.UPDATE)) {
                ClubTaxonomiaError.Forbidden
            }
            val clubId = ClubId.of(actor.clubId)
            val group = GroupId.of(groupId)
            val student = PersonId.of(studentId)

            ensureGroupOfClub(groupRepository, clubId, group)
            ensure(studentLookup.isStudent(clubId, student)) { ClubTaxonomiaError.StudentNotFound }

            groupRepository.upsertOverride(clubId, group, student, included)

            // Se pide el detalle ya recalculado, en la misma transacción: la pantalla necesita el nuevo recuento y
            // el nuevo origen de cada miembro, y pedirlo aparte daría una lectura fuera de esta transacción.
            val detail = groupRepository.findDetail(clubId, group) ?: raise(ClubTaxonomiaError.GroupNotFound)
            val members = detail.members.mapTo(mutableSetOf()) { it.member.id }
            groupMembershipPublisher.publish(clubId, actor.userId, group, members)

            detail
        }
}
