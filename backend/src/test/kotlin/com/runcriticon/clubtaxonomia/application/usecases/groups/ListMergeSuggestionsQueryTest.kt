package com.runcriticon.clubtaxonomia.application.usecases.groups

import arrow.core.getOrElse
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.MergeSuggestionRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.group.GroupName
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestionOverview
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestionType
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID

class ListMergeSuggestionsQueryTest :
    FunSpec({
        val club = ClubId.of(UUID.randomUUID())

        val suggestionRepository = mockk<MergeSuggestionRepository>(relaxed = true)
        val useCase = ListMergeSuggestionsQuery(suggestionRepository, mockk(relaxed = true))

        beforeEach { clearMocks(suggestionRepository) }

        listOf(Role.ADMIN, Role.ENTRENADOR).forEach { role ->
            test("$role lista las sugerencias activas del club") {
                val actor = Principal(userId = UUID.randomUUID(), clubId = club.value, role = role)
                val overview =
                    MergeSuggestionOverview(
                        groupAId = GroupId.new(),
                        groupAName = GroupName.of("Elite").getOrElse { error("nombre inválido") },
                        groupBId = GroupId.new(),
                        groupBName = GroupName.of("Elite masculino").getOrElse { error("nombre inválido") },
                        type = MergeSuggestionType.DUPLICADO,
                        calculatedAt = Instant.parse("2026-08-25T10:00:00Z"),
                    )
                every { suggestionRepository.listActive(club) } returns listOf(overview)

                val result = useCase.execute(actor).shouldBeRight()

                result shouldBe listOf(overview)
            }
        }

        test("un ALUMNO no puede listar sugerencias de fusion") {
            val alumno = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ALUMNO)

            useCase.execute(alumno).shouldBeLeft(ClubTaxonomiaError.Forbidden)

            verify(exactly = 0) { suggestionRepository.listActive(any()) }
        }
    })
