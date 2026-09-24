package com.runcriticon.clubtaxonomia.application.usecases.taxonomy

import com.runcriticon.clubtaxonomia.application.ClubTaxonomiaAccessAuditor
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.TaxonomyRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.testing.PrincipalBuilder
import com.runcriticon.testing.TestClubs
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

/**
 * `ArchiveTagKeyCommand`, `ArchiveTagValueCommand` y `ChangeTagValueMetadataCommand` no tenían ningún test de
 * acceso cruzado (P0-3, auditoría de testing 2026-09). Los tres cargan siempre `findByClub(ClubId.of(actor.clubId))`
 * (`MutateTaxonomy.kt`) — nunca leen ni un `clubId` de parámetro — así que un `keyId`/`valueId` de otro club no
 * puede llevar a una lectura ni a una escritura cruzada: solo puede dar el mismo `NotFound` que un id inexistente
 * (excepción anti-enumeración de ADR-0009 D14, mismo criterio que `GetGroupDetailQuery`). Este test prueba
 * justamente eso: la taxonomía del club del actor no contiene el id, y el comando no persiste nada.
 */
class TaxonomyCrossClubAccessTest :
    FunSpec({
        val club = TestClubs.newClub()
        val admin = PrincipalBuilder().admin().inClub(club).build()
        val idDeOtroClub = UUID.randomUUID()

        val repository = mockk<TaxonomyRepository>(relaxed = true)
        val groupRepository = mockk<GroupRepository>(relaxed = true)
        val auditor = mockk<ClubTaxonomiaAccessAuditor>(relaxed = true)

        beforeTest { every { repository.findByClub(club) } returns Taxonomy.empty(club) }

        test("ArchiveTagKeyCommand con un keyId de otro club devuelve TagKeyNotFound y no persiste") {
            ArchiveTagKeyCommand(repository, groupRepository, auditor)
                .execute(admin, idDeOtroClub)
                .shouldBeLeft(ClubTaxonomiaError.TagKeyNotFound)

            verify(exactly = 0) { repository.save(any(), any()) }
            verify(exactly = 0) { groupRepository.findGroupIdsByAnyRequiredTagValue(any(), any()) }
        }

        test("ArchiveTagValueCommand con un valueId de otro club devuelve TagValueNotFound y no persiste") {
            ArchiveTagValueCommand(repository, groupRepository, auditor)
                .execute(admin, idDeOtroClub)
                .shouldBeLeft(ClubTaxonomiaError.TagValueNotFound)

            verify(exactly = 0) { repository.save(any(), any()) }
            verify(exactly = 0) { groupRepository.findGroupIdsByAnyRequiredTagValue(any(), any()) }
        }

        test("ChangeTagValueMetadataCommand con un valueId de otro club devuelve TagValueNotFound y no persiste") {
            ChangeTagValueMetadataCommand(repository, auditor)
                .execute(admin, idDeOtroClub, metadata = null)
                .shouldBeLeft(ClubTaxonomiaError.TagValueNotFound)

            verify(exactly = 0) { repository.save(any(), any()) }
        }
    })
