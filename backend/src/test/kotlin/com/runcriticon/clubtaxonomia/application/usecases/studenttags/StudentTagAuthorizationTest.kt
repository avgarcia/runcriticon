package com.runcriticon.clubtaxonomia.application.usecases.studenttags

import arrow.core.Either
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentLookup
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentTagRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.application.usecases.groups.GroupMembershipPublisher
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

/**
 * Clasificar alumnos lo pueden ADMIN y ENTRENADOR; el alumno no. Un rechazo tiene que cortar **antes** de tocar nada,
 * así que cada caso comprueba también que no se consultó al alumno ni se escribió.
 *
 * Una entrada por operación, para que añadir una quinta sin su guard falle aquí.
 */
class StudentTagAuthorizationTest :
    FunSpec({
        val clubId = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val studentId = UuidCreator.getTimeOrderedEpoch()
        val valueId = UuidCreator.getTimeOrderedEpoch()

        fun principal(role: Role) = Principal(userId = UUID.randomUUID(), clubId = clubId.value, role = role)

        val lookup = mockk<StudentLookup>(relaxed = true)
        val tags = mockk<StudentTagRepository>(relaxed = true)
        val taxonomy = mockk<TaxonomyRepository>(relaxed = true)
        val groups = mockk<GroupRepository>(relaxed = true)
        val membershipPublisher = mockk<GroupMembershipPublisher>(relaxed = true)
        val classification = StudentClassification(lookup, tags, taxonomy, groups, membershipPublisher)

        val operations: List<Pair<String, (Principal) -> Either<ClubTaxonomiaError, Any>>> =
            listOf(
                "ListStudentTagsQuery" to { actor ->
                    ListStudentTagsQuery(classification).execute(actor, studentId)
                },
                "ReplaceStudentTagsCommand" to { actor ->
                    ReplaceStudentTagsCommand(classification, tags).execute(actor, studentId, listOf(valueId))
                },
                "AssignStudentTagCommand" to { actor ->
                    AssignStudentTagCommand(classification, tags).execute(actor, studentId, valueId)
                },
                "UnassignStudentTagCommand" to { actor ->
                    UnassignStudentTagCommand(classification, tags).execute(actor, studentId, valueId)
                },
            )

        beforeEach { clearMocks(lookup, tags, taxonomy) }

        operations.forEach { (name, operation) ->
            test("$name rechaza al ALUMNO sin consultar ni escribir nada") {
                operation(principal(Role.ALUMNO)).shouldBeLeft(ClubTaxonomiaError.Forbidden)

                verify(exactly = 0) { lookup.isStudent(any(), any()) }
                verify(exactly = 0) { tags.replace(any(), any(), any()) }
                verify(exactly = 0) { tags.add(any(), any(), any()) }
                verify(exactly = 0) { tags.remove(any(), any(), any()) }
            }
        }
    })
