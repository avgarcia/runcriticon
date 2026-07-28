package com.runcriticon.clubtaxonomia.application.usecases.taxonomy

import arrow.core.Either
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

/**
 * *Gestionar club y taxonomía* es escritura de admin: el entrenador solo lee y el alumno ni eso. Un rechazo no puede
 * dejar rastro, así que cada caso comprueba también que no se guardó nada.
 */
class TaxonomyAuthorizationTest :
    FunSpec({
        val clubId = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))

        fun principal(role: Role) = Principal(userId = UUID.randomUUID(), clubId = clubId.value, role = role)

        val repository = mockk<TaxonomyRepository>(relaxed = true)
        val someId = UUID.randomUUID()

        // Una entrada por comando de escritura, para que añadir un caso de uso sin su guard falle aquí.
        val writeCommands: List<Pair<String, (Principal) -> Either<ClubTaxonomiaError, Any>>> =
            listOf(
                "CreateTagKeyCommand" to { actor -> CreateTagKeyCommand(repository).execute(actor, "Nivel") },
                "RenameTagKeyCommand" to { actor -> RenameTagKeyCommand(repository).execute(actor, someId, "Nivel") },
                "ArchiveTagKeyCommand" to { actor -> ArchiveTagKeyCommand(repository).execute(actor, someId) },
                "AddTagValueCommand" to { actor -> AddTagValueCommand(repository).execute(actor, someId, "5K") },
                "RenameTagValueCommand" to { actor -> RenameTagValueCommand(repository).execute(actor, someId, "5K") },
                "ArchiveTagValueCommand" to { actor -> ArchiveTagValueCommand(repository).execute(actor, someId) },
            )

        beforeTest {
            clearMocks(repository)
            every { repository.findByClub(clubId) } returns Taxonomy.empty(clubId)
        }

        listOf(Role.ENTRENADOR, Role.ALUMNO).forEach { role ->
            writeCommands.forEach { (name, invoke) ->
                test("$name rechaza a $role con Forbidden y no guarda") {
                    invoke(principal(role)).shouldBeLeft(ClubTaxonomiaError.Forbidden)
                    verify(exactly = 0) { repository.save(any(), any()) }
                }
            }
        }

        test("ListTaxonomyQuery permite consultar al entrenador") {
            ListTaxonomyQuery(repository).execute(principal(Role.ENTRENADOR)).shouldBeRight()
        }

        test("ListTaxonomyQuery rechaza al alumno con Forbidden") {
            ListTaxonomyQuery(repository)
                .execute(principal(Role.ALUMNO))
                .shouldBeLeft(ClubTaxonomiaError.Forbidden)
        }

        test("ListTaxonomyQuery devuelve la taxonomía del club del actor, no la de otro") {
            val otherClub = ClubId.of(UUID.randomUUID())
            every { repository.findByClub(otherClub) } returns Taxonomy.empty(otherClub)

            ListTaxonomyQuery(repository).execute(principal(Role.ADMIN)).shouldBeRight()

            verify(exactly = 1) { repository.findByClub(clubId) }
            verify(exactly = 0) { repository.findByClub(otherClub) }
        }
    })
