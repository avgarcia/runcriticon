package com.runcriticon.planificacion.domain

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.UUID

class PersonalizationTest :
    FunSpec({
        val session = SessionId.new()
        val student = PersonId.of(UUID.randomUUID())
        val override = SessionOverride.create(type = SessionType.DESCANSO).shouldBeRight()

        test("una personalizacion sin mensaje se crea sin error") {
            val personalization =
                Personalization.create(sessionId = session, studentId = student, override = override).shouldBeRight()

            personalization.sessionId shouldBe session
            personalization.studentId shouldBe student
            personalization.override shouldBe override
            personalization.messageToStudent shouldBe null
        }

        test("una personalizacion con mensaje dentro de longitud se crea sin error") {
            val personalization =
                Personalization
                    .create(
                        sessionId = session,
                        studentId = student,
                        override = override,
                        messageToStudent = "Vuelves de lesión, no te pases hoy.",
                    ).shouldBeRight()

            personalization.messageToStudent shouldBe "Vuelves de lesión, no te pases hoy."
        }

        test("un mensaje de mas de 1000 caracteres se rechaza") {
            val error =
                Personalization
                    .create(
                        sessionId = session,
                        studentId = student,
                        override = override,
                        messageToStudent = "a".repeat(1001),
                    ).shouldBeLeft()

            error.shouldBeInstanceOf<PlanificacionError.InvalidInput>().field shouldBe "mensajeAlAlumno"
        }

        test("un mensaje de exactamente 1000 caracteres se acepta") {
            Personalization
                .create(
                    sessionId = session,
                    studentId = student,
                    override = override,
                    messageToStudent = "a".repeat(1000),
                ).shouldBeRight()
        }
    })
