package com.runcriticon.clubtaxonomia.application.usecases.groups

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.MergeSuggestionRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestion
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestionType
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

class DismissMergeSuggestionCommandTest :
    FunSpec({
        val club = ClubId.of(UUID.randomUUID())
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)
        val groupIdX = GroupId.new()
        val groupIdY = GroupId.new()

        val suggestionRepository = mockk<MergeSuggestionRepository>(relaxed = true)
        val useCase = DismissMergeSuggestionCommand(suggestionRepository, mockk(relaxed = true), mockk(relaxed = true))

        beforeEach { clearMocks(suggestionRepository) }

        test("descarta la sugerencia canonicalizando el par sin importar el orden de entrada") {
            val (a, b) = MergeSuggestion.canonicalPair(groupIdX, groupIdY)
            every { suggestionRepository.dismiss(club, a, b, MergeSuggestionType.DUPLICADO) } returns true

            useCase
                .execute(admin, groupIdY.value, groupIdX.value, MergeSuggestionType.DUPLICADO)
                .shouldBeRight()

            verify { suggestionRepository.dismiss(club, a, b, MergeSuggestionType.DUPLICADO) }
        }

        test("si no hay sugerencia activa con esa clave devuelve MergeSuggestionNotFound") {
            every { suggestionRepository.dismiss(any(), any(), any(), any()) } returns false

            useCase
                .execute(admin, groupIdX.value, groupIdY.value, MergeSuggestionType.MICRO)
                .shouldBeLeft(ClubTaxonomiaError.MergeSuggestionNotFound)
        }

        test("una sugerencia con la misma clave pero de otro club da MergeSuggestionNotFound (P0-3)") {
            val (a, b) = MergeSuggestion.canonicalPair(groupIdX, groupIdY)
            val otherClub = ClubId.of(UUID.randomUUID())
            every { suggestionRepository.dismiss(otherClub, a, b, MergeSuggestionType.DUPLICADO) } returns true
            every { suggestionRepository.dismiss(club, a, b, MergeSuggestionType.DUPLICADO) } returns false

            useCase
                .execute(admin, groupIdX.value, groupIdY.value, MergeSuggestionType.DUPLICADO)
                .shouldBeLeft(ClubTaxonomiaError.MergeSuggestionNotFound)

            verify(exactly = 0) { suggestionRepository.dismiss(otherClub, any(), any(), any()) }
        }

        test("un ALUMNO no puede descartar sugerencias") {
            val alumno = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ALUMNO)

            useCase
                .execute(alumno, groupIdX.value, groupIdY.value, MergeSuggestionType.MICRO)
                .shouldBeLeft(ClubTaxonomiaError.Forbidden)

            verify(exactly = 0) { suggestionRepository.dismiss(any(), any(), any(), any()) }
        }
    })
