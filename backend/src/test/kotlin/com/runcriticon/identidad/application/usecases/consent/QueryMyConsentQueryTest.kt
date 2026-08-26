package com.runcriticon.identidad.application.usecases.consent

import com.runcriticon.identidad.application.ports.outbound.persistence.ConsentRepository
import com.runcriticon.identidad.domain.consent.Consent
import com.runcriticon.identidad.domain.consent.ConsentText
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID

class QueryMyConsentQueryTest :
    FunSpec({
        val club = ClubId.of(UUID.randomUUID())
        val alumno = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ALUMNO)
        val consentRepository = mockk<ConsentRepository>()
        val query = QueryMyConsentQuery(consentRepository)

        test("sin ninguna fila, devuelve null (PENDIENTE)") {
            every { consentRepository.findLatestByUserId(club, UserId.of(alumno.userId)) } returns null

            query.execute(alumno).shouldBeNull()
        }

        test("devuelve la fila mas reciente del propio alumno") {
            val consent =
                Consent.grant(
                    UserId.of(alumno.userId),
                    club,
                    ConsentText.CURRENT_VERSION,
                    "203.0.113.10",
                    "agent",
                    Instant.now(),
                )
            every { consentRepository.findLatestByUserId(club, UserId.of(alumno.userId)) } returns consent

            query.execute(alumno) shouldBe consent
        }
    })
