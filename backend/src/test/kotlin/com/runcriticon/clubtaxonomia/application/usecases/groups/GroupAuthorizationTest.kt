package com.runcriticon.clubtaxonomia.application.usecases.groups

import arrow.core.Either
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentLookup
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.InMemoryTaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupDetail
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

/**
 * Los grupos los arma quien entrena: el admin y el entrenador, tanto para crearlos y previsualizarlos como para
 * ajustar a mano quién está dentro. El alumno queda fuera de todas las operaciones. Un rechazo no puede dejar rastro ni
 * llegar a consultar la base.
 */
class GroupAuthorizationTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val grupo = Group.create(club, "Maratón Valencia avanzado").shouldBeRight()
        val alumno = PersonId.of(UuidCreator.getTimeOrderedEpoch())

        fun principal(role: Role) = Principal(userId = UUID.randomUUID(), clubId = club.value, role = role)

        lateinit var groups: InMemoryGroupRepository
        lateinit var taxonomy: InMemoryTaxonomyRepository
        val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

        // Una entrada por caso de uso, para que añadir uno sin su guard falle aquí.
        lateinit var useCases: List<Pair<String, (Principal) -> Either<ClubTaxonomiaError, Any>>>

        beforeEach {
            groups =
                InMemoryGroupRepository(
                    existing = mapOf(grupo.id to GroupDetail(grupo, members = emptyList(), exclusions = emptyList())),
                )
            taxonomy = InMemoryTaxonomyRepository(Taxonomy.empty(club))
            useCases =
                listOf(
                    "CreateGroupCommand" to { actor: Principal ->
                        CreateGroupCommand(taxonomy, groups).execute(actor, "Maratón Valencia", emptyList())
                    },
                    "PreviewGroupMembersQuery" to { actor: Principal ->
                        PreviewGroupMembersQuery(taxonomy, groups).execute(actor, emptyList())
                    },
                    "ListGroupsQuery" to { actor: Principal ->
                        ListGroupsQuery(groups).execute(actor)
                    },
                    "GetGroupDetailQuery" to { actor: Principal ->
                        GetGroupDetailQuery(groups).execute(actor, grupo.id.value)
                    },
                    "OverrideGroupMembershipCommand" to { actor: Principal ->
                        OverrideGroupMembershipCommand(groups, AlwaysAStudent, eventPublisher)
                            .execute(actor, grupo.id.value, alumno.value, included = true)
                    },
                    "ClearGroupMembershipOverrideCommand" to { actor: Principal ->
                        ClearGroupMembershipOverrideCommand(groups).execute(actor, grupo.id.value, alumno.value)
                    },
                    // ASSIGN_COACH (asignar/desvincular entrenadores) es solo ADMIN, así que no entra en esta lista
                    // simétrica -- tiene su propio test, GroupCoachAssignmentAuthorizationTest. Leer quién lleva un
                    // grupo sí es GROUP:LIST, igual que el resto de lecturas de este fichero.
                    "ListGroupCoachesQuery" to { actor: Principal ->
                        ListGroupCoachesQuery(groups).execute(actor, grupo.id.value)
                    },
                )
        }

        test("el alumno no puede ninguna de las operaciones de grupo, y no se toca la base") {
            useCases.forEach { (name, invoke) ->
                withClue(name) {
                    invoke(principal(Role.ALUMNO)).shouldBeLeft(ClubTaxonomiaError.Forbidden)
                }
            }

            groups.saveCount shouldBe 0
            groups.previewCount shouldBe 0
            groups.listCount shouldBe 0
            groups.overrideCount shouldBe 0
            groups.deleteCalls.size shouldBe 0
            groups.detailCalls.size shouldBe 0
            groups.findCoachesCalls.size shouldBe 0
        }

        listOf(Role.ADMIN, Role.ENTRENADOR).forEach { role ->
            test("$role puede todas las operaciones de grupo") {
                useCases.forEach { (name, invoke) ->
                    withClue(name) { invoke(principal(role)).shouldBeRight() }
                }
            }
        }

        test("todos los casos de uso operan sobre el club del actor, no sobre otro") {
            val actor = principal(Role.ENTRENADOR)

            useCases.forEach { (name, invoke) -> withClue(name) { invoke(actor).shouldBeRight() } }

            groups.saved.single().first shouldBe club
            groups.previewCalls.single().first shouldBe club
            groups.listCalls.single() shouldBe club
            groups.overrideCalls.single().first shouldBe club
            groups.deleteCalls.single().first shouldBe club
            groups.detailCalls.forEach { it.first shouldBe club }
            groups.findCoachesCalls.forEach { it.first shouldBe club }
        }
    })

/** El ajuste manual exige que la persona sea alumno del club; aquí siempre lo es, para aislar la autorización. */
private object AlwaysAStudent : StudentLookup {
    override fun isStudent(
        clubId: ClubId,
        personId: PersonId,
    ): Boolean = true
}
