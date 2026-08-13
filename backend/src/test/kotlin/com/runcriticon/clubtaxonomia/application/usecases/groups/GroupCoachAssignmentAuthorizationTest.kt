package com.runcriticon.clubtaxonomia.application.usecases.groups

import arrow.core.Either
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupDetail
import com.runcriticon.clubtaxonomia.domain.person.PersonId
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
 * Asignar o desvincular un entrenador de un grupo es **solo del ADMIN**, a diferencia del resto de operaciones de
 * grupo (crear, previsualizar, listar, ajustar a mano, cubiertas en [GroupAuthorizationTest]), que comparten ADMIN
 * y ENTRENADOR. Test aparte, no una entrada más en la lista de [GroupAuthorizationTest], precisamente porque el
 * permiso NO es simétrico entre esos dos roles: mezclarlas ahí rompería la aserción "ambos roles pueden todo".
 *
 * Es la decisión que corrige el comentario que dejó `AuthorizationMatrix.kt` al escribir LAL-92: dejar esto en
 * `GROUP:UPDATE` habría permitido que un entrenador se autoasignara a cualquier grupo.
 */
class GroupCoachAssignmentAuthorizationTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val grupo = Group.create(club, "Maratón Valencia avanzado").shouldBeRight()
        val entrenador = PersonId.of(UuidCreator.getTimeOrderedEpoch())

        fun principal(role: Role) = Principal(userId = UUID.randomUUID(), clubId = club.value, role = role)

        lateinit var groups: InMemoryGroupRepository
        lateinit var useCases: List<Pair<String, (Principal) -> Either<ClubTaxonomiaError, Any>>>
        val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)

        beforeEach {
            groups =
                InMemoryGroupRepository(
                    existing = mapOf(grupo.id to GroupDetail(grupo, members = emptyList(), exclusions = emptyList())),
                )
            useCases =
                listOf(
                    "AssignCoachToGroupCommand" to { actor: Principal ->
                        AssignCoachToGroupCommand(groups, AlwaysACoach, eventPublisher)
                            .execute(actor, grupo.id.value, entrenador.value)
                    },
                    "UnassignCoachFromGroupCommand" to { actor: Principal ->
                        UnassignCoachFromGroupCommand(groups, eventPublisher)
                            .execute(actor, grupo.id.value, entrenador.value)
                    },
                )
        }

        listOf(Role.ENTRENADOR, Role.ALUMNO).forEach { role ->
            test("$role no puede asignar ni desvincular entrenadores, y no se toca la base") {
                useCases.forEach { (name, invoke) ->
                    withClue(name) { invoke(principal(role)).shouldBeLeft(ClubTaxonomiaError.Forbidden) }
                }

                groups.assignCoachCalls.size shouldBe 0
                groups.unassignCoachCalls.size shouldBe 0
            }
        }

        test("el admin puede asignar y desvincular entrenadores") {
            useCases.forEach { (name, invoke) -> withClue(name) { invoke(principal(Role.ADMIN)).shouldBeRight() } }
        }
    })
