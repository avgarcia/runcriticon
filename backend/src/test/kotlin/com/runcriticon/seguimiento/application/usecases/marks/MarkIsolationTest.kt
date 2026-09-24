package com.runcriticon.seguimiento.application.usecases.marks

import com.runcriticon.seguimiento.domain.RaceDistance
import com.runcriticon.seguimiento.domain.StudentId
import com.runcriticon.testing.MutableClock
import com.runcriticon.testing.TestPrincipals
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import java.time.Instant

/**
 * Aislamiento entre dos alumnos del mismo club (ADR-0009 D14, ADR-0002 D7): `RecordMarkCommand`,
 * `WithdrawMarkCommand` y `GetMyMarksQuery` no reciben nunca un `studentId` como parámetro (anti-IDOR, ver KDoc
 * de `RecordMarkCommand`) — siempre derivan `StudentId.of(actor.userId)`. Lo que hay que probar no es un
 * `Forbidden` (no hay id ajeno posible que pasar), sino que la llamada al repositorio siempre lleva el
 * `studentId` del actor que llamó, nunca el de otro alumno.
 */
class MarkIsolationTest :
    FunSpec({
        val (alumnoA, alumnoB) = TestPrincipals.twoStudentsSameClub()
        val studentIdA = StudentId.of(alumnoA.userId)
        val studentIdB = StudentId.of(alumnoB.userId)
        val clock = MutableClock(Instant.parse("2026-09-24T10:00:00Z"))

        test("RecordMarkCommand guarda cada marca bajo el studentId de quien la registró") {
            val repository = InMemoryStudentMarkRepository()
            val command = RecordMarkCommand(repository, mockk(relaxed = true), clock)

            command.execute(alumnoA, RaceDistance.TEN_K, timeSeconds = 2850).shouldBeRight()
            command.execute(alumnoB, RaceDistance.TEN_K, timeSeconds = 3100).shouldBeRight()

            repository.upsertCalls.map { it.studentId } shouldBe listOf(studentIdA, studentIdB)
            studentIdA shouldNotBe studentIdB
        }

        test("WithdrawMarkCommand retira solo la marca de quien llama, nunca la de otro alumno") {
            val repository = InMemoryStudentMarkRepository()
            val command = WithdrawMarkCommand(repository, mockk(relaxed = true), clock)

            command.execute(alumnoA, RaceDistance.FIVE_K).shouldBeRight()
            command.execute(alumnoB, RaceDistance.FIVE_K).shouldBeRight()

            repository.deletedBy shouldBe listOf(studentIdA, studentIdB)
        }

        test("GetMyMarksQuery consulta siempre bajo el studentId de quien pregunta") {
            val repository = InMemoryStudentMarkRepository()
            val query = GetMyMarksQuery(repository)

            query.execute(alumnoA).shouldBeRight()
            query.execute(alumnoB).shouldBeRight()

            repository.findAllCalledBy shouldBe listOf(studentIdA, studentIdB)
        }
    })
