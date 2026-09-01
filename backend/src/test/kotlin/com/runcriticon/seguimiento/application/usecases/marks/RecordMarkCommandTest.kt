package com.runcriticon.seguimiento.application.usecases.marks

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.seguimiento.api.events.MarcaActualizada
import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.SeguimientoError
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.shared.tenancy.ClubId
import com.runcriticon.testing.MutableClock
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.UUID

class RecordMarkCommandTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val alumno = Principal(userId = UuidCreator.getTimeOrderedEpoch(), clubId = club.value, role = Role.ALUMNO)
        val now = MutableClock(Instant.parse("2026-08-28T18:00:00Z"))

        fun newCommand(
            repository: InMemoryStudentMarkRepository = InMemoryStudentMarkRepository(),
            eventPublisher: ApplicationEventPublisher = mockk(relaxed = true),
        ) = RecordMarkCommand(repository, eventPublisher, now)

        test("la primera marca de una distancia se guarda") {
            val repository = InMemoryStudentMarkRepository()
            val command = newCommand(repository = repository)

            val mark = command.execute(alumno, RaceDistance.TEN_K, timeSeconds = 2850).shouldBeRight()

            mark.distance shouldBe RaceDistance.TEN_K
            mark.timeSeconds shouldBe 2850
            repository.upsertCalls.single().clubId shouldBe club
        }

        test("una segunda marca de la misma distancia sobreescribe, sin duplicar") {
            val repository = InMemoryStudentMarkRepository()
            val command = newCommand(repository = repository)

            command.execute(alumno, RaceDistance.TEN_K, timeSeconds = 2850).shouldBeRight()
            command.execute(alumno, RaceDistance.TEN_K, timeSeconds = 2700).shouldBeRight()

            val studentId = StudentId.of(alumno.userId)
            repository.upsertCalls.size shouldBe 2
            repository.findAll(club, studentId).size shouldBe 1
            repository.findAll(club, studentId)[RaceDistance.TEN_K]?.timeSeconds shouldBe 2700
        }

        test("tiempo cero es InvalidInput y no persiste nada") {
            val repository = InMemoryStudentMarkRepository()
            val command = newCommand(repository = repository)

            command
                .execute(alumno, RaceDistance.FIVE_K, timeSeconds = 0)
                .shouldBeLeft(SeguimientoError.InvalidInput(field = "tiempoSegundos", reason = "not_positive"))

            repository.upsertCalls.size shouldBe 0
        }

        test("emite MarcaActualizada con la distancia y el tiempo") {
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val slot = slot<MarcaActualizada>()
            val command = newCommand(eventPublisher = eventPublisher)

            command.execute(alumno, RaceDistance.HALF_MARATHON, timeSeconds = 6300).shouldBeRight()

            verify { eventPublisher.publishEvent(capture(slot)) }
            val event = slot.captured
            event.aggregateId shouldBe alumno.userId
            event.clubId shouldBe club.value
            event.distancia shouldBe "21K"
            event.tiempoSegundos shouldBe 6300
        }

        test("una marca invalida no emite ningun evento") {
            val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
            val command = newCommand(eventPublisher = eventPublisher)

            command.execute(alumno, RaceDistance.FIVE_K, timeSeconds = -1).shouldBeLeft()

            verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
        }
    })
