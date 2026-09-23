package com.runcriticon.clubtaxonomia.application.usecases.taxonomy

import arrow.core.Either
import com.runcriticon.clubtaxonomia.application.ClubTaxonomiaAccessAuditor
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentTagRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyType
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.PrincipalBuilder
import com.runcriticon.testing.TestClubs
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
        val club = TestClubs.newClub()

        fun principal(role: Role) = PrincipalBuilder().role(role).inClub(club).build()

        val repository = mockk<TaxonomyRepository>(relaxed = true)
        val groupRepository = mockk<GroupRepository>(relaxed = true)
        val studentTagRepository = mockk<StudentTagRepository>(relaxed = true)
        val someId = UUID.randomUUID()

        val auditor = mockk<ClubTaxonomiaAccessAuditor>(relaxed = true)

        // Una entrada por comando de escritura, para que añadir un caso de uso sin su guard falle aquí.
        val writeCommands: List<Pair<String, (Principal) -> Either<ClubTaxonomiaError, Any>>> =
            listOf(
                "CreateTagKeyCommand" to { actor -> CreateTagKeyCommand(repository, auditor).execute(actor, "Nivel") },
                "RenameTagKeyCommand" to { actor ->
                    RenameTagKeyCommand(repository, auditor).execute(actor, someId, "Nivel")
                },
                "ArchiveTagKeyCommand" to { actor ->
                    ArchiveTagKeyCommand(repository, groupRepository, auditor).execute(actor, someId)
                },
                "AddTagValueCommand" to
                    { actor -> AddTagValueCommand(repository, auditor).execute(actor, someId, "5K") },
                "RenameTagValueCommand" to { actor ->
                    RenameTagValueCommand(repository, auditor).execute(actor, someId, "5K")
                },
                "ArchiveTagValueCommand" to { actor ->
                    ArchiveTagValueCommand(repository, groupRepository, auditor).execute(actor, someId)
                },
                "ReactivateTagKeyCommand" to { actor ->
                    ReactivateTagKeyCommand(repository, auditor).execute(actor, someId)
                },
                "ReactivateTagValueCommand" to { actor ->
                    ReactivateTagValueCommand(repository, auditor).execute(actor, someId)
                },
                "ChangeTagKeyTypeCommand" to { actor ->
                    ChangeTagKeyTypeCommand(repository, auditor).execute(actor, someId, TagKeyType.RACE)
                },
                "ChangeTagValueMetadataCommand" to { actor ->
                    ChangeTagValueMetadataCommand(repository, auditor).execute(actor, someId, null)
                },
                "GetTagKeyArchiveImpactQuery" to { actor ->
                    GetTagKeyArchiveImpactQuery(repository, studentTagRepository, groupRepository, auditor)
                        .execute(actor, someId)
                },
                "GetTagValueArchiveImpactQuery" to { actor ->
                    GetTagValueArchiveImpactQuery(repository, studentTagRepository, groupRepository, auditor)
                        .execute(actor, someId)
                },
            )

        beforeTest {
            clearMocks(repository)
            every { repository.findByClub(club) } returns Taxonomy.empty(club)
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
            ListTaxonomyQuery(repository, auditor).execute(principal(Role.ENTRENADOR)).shouldBeRight()
        }

        test("ListTaxonomyQuery rechaza al alumno con Forbidden") {
            ListTaxonomyQuery(repository, auditor)
                .execute(principal(Role.ALUMNO))
                .shouldBeLeft(ClubTaxonomiaError.Forbidden)
        }

        test("ListTaxonomyQuery devuelve la taxonomía del club del actor, no la de otro") {
            val otherClub = ClubId.of(UUID.randomUUID())
            every { repository.findByClub(otherClub) } returns Taxonomy.empty(otherClub)

            ListTaxonomyQuery(repository, auditor).execute(principal(Role.ADMIN)).shouldBeRight()

            verify(exactly = 1) { repository.findByClub(club) }
            verify(exactly = 0) { repository.findByClub(otherClub) }
        }
    })
