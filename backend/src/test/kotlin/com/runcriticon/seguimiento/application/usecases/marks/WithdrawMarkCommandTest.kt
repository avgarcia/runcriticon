package com.runcriticon.seguimiento.application.usecases.marks

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.seguimiento.api.events.MarcaRetirada
import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.StudentMark
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.MutableClock
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.UUID

class WithdrawMarkCommandTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val alumno = Principal(userId = UuidCreator.getTimeOrderedEpoch(), clubId = club.value, role = Role.ALUMNO)
        val now = MutableClock(Instant.parse("2026-08-28T18:00:00Z"))

        fun newCommand(
            repository: InMemoryStudentMarkRepository,
            eventPublisher: ApplicationEventPublisher = mockk(relaxed = true),
        ) = WithdrawMarkCommand(repository, eventPublisher, now)

        test("borrar una marca existente emite MarcaRetirada") {
            val tenKMark = StudentMark(RaceDistance.TEN_K, timeSeconds = 2850, modifiedAt = now.instant())
            val existing = mapOf(RaceDistance.TEN_K to tenKMark)
            val repository = InMemoryStudentMarkRepository(existing)
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val slot = slot<MarcaRetirada>()
            val command = newCommand(repository, eventPublisher)

            command.execute(alumno, RaceDistance.TEN_K).shouldBeRight()

            verify { eventPublisher.publishEvent(capture(slot)) }
            slot.captured.aggregateId shouldBe alumno.userId
            slot.captured.distancia shouldBe "10K"
            repository.deleteCalls shouldBe listOf(RaceDistance.TEN_K)
        }

        test("borrar una marca que no existia es idempotente y no emite ningun evento") {
            val repository = InMemoryStudentMarkRepository()
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val command = newCommand(repository, eventPublisher)

            command.execute(alumno, RaceDistance.MARATHON).shouldBeRight()

            verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
            repository.deleteCalls shouldBe listOf(RaceDistance.MARATHON)
        }
    })
