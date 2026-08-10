package com.runcriticon.clubtaxonomia.application.usecases.groups

import arrow.core.Either
import com.runcriticon.clubtaxonomia.application.usecases.taxonomy.InMemoryTaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

/**
 * Los grupos los arma quien entrena: el admin y el entrenador, tanto para crearlos como para previsualizarlos. El
 * alumno queda fuera de las dos operaciones. Un rechazo no puede dejar rastro ni llegar a consultar la base.
 */
class GroupAuthorizationTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))

        fun principal(role: Role) = Principal(userId = UUID.randomUUID(), clubId = club.value, role = role)

        lateinit var groups: InMemoryGroupRepository
        lateinit var taxonomy: InMemoryTaxonomyRepository

        // Una entrada por caso de uso, para que añadir uno sin su guard falle aquí.
        lateinit var useCases: List<Pair<String, (Principal) -> Either<ClubTaxonomiaError, Any>>>

        beforeEach {
            groups = InMemoryGroupRepository()
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
                )
        }

        test("el alumno no puede crear, previsualizar ni listar, y no se toca la base") {
            useCases.forEach { (name, invoke) ->
                withClue(name) {
                    invoke(principal(Role.ALUMNO)).shouldBeLeft(ClubTaxonomiaError.Forbidden)
                }
            }

            groups.saveCount shouldBe 0
            groups.previewCount shouldBe 0
            groups.listCount shouldBe 0
        }

        listOf(Role.ADMIN, Role.ENTRENADOR).forEach { role ->
            test("$role puede crear, previsualizar y listar grupos") {
                CreateGroupCommand(taxonomy, groups)
                    .execute(principal(role), "Trail finde", emptyList())
                    .shouldBeRight()
                PreviewGroupMembersQuery(taxonomy, groups)
                    .execute(principal(role), emptyList())
                    .shouldBeRight()
                ListGroupsQuery(groups).execute(principal(role)).shouldBeRight()
            }
        }

        test("los tres casos de uso operan sobre el club del actor, no sobre otro") {
            val actor = principal(Role.ENTRENADOR)

            CreateGroupCommand(taxonomy, groups).execute(actor, "Iniciación", emptyList()).shouldBeRight()
            PreviewGroupMembersQuery(taxonomy, groups).execute(actor, emptyList()).shouldBeRight()
            ListGroupsQuery(groups).execute(actor).shouldBeRight()

            groups.saved.single().first shouldBe club
            groups.previewCalls.single().first shouldBe club
            groups.listCalls.single() shouldBe club
        }
    })
