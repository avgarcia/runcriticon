package com.runcriticon.planificacion.domain

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.LocalDate

class SessionTest :
    FunSpec({
        val monday = LocalDate.of(2026, 8, 17)

        test("una sesion de rodaje con distancia y ritmo absoluto se crea sin error") {
            val session =
                Session
                    .create(
                        day = monday,
                        type = SessionType.RODAJE,
                        volume = SessionVolume.Distance(meters = 8000),
                        pace = Pace.Absoluto(secondsPerKm = 330),
                        notes = "Ritmo suave",
                    ).shouldBeRight()

            session.type shouldBe SessionType.RODAJE
            session.volume shouldBe SessionVolume.Distance(meters = 8000)
            session.notes shouldBe "Ritmo suave"
        }

        test("una sesion de descanso con volumen se rechaza") {
            val error =
                Session
                    .create(
                        day = monday,
                        type = SessionType.DESCANSO,
                        volume = SessionVolume.Distance(meters = 1000),
                    ).shouldBeLeft()

            error.shouldBeInstanceOf<PlanificacionError.InvalidInput>().field shouldBe "tipo"
        }

        test("una sesion de descanso con ritmo se rechaza") {
            val error =
                Session
                    .create(
                        day = monday,
                        type = SessionType.DESCANSO,
                        pace = Pace.Absoluto(secondsPerKm = 300),
                    ).shouldBeLeft()

            error.shouldBeInstanceOf<PlanificacionError.InvalidInput>().field shouldBe "tipo"
        }

        test("una sesion de tempo con ritmo relativo a una marca se crea sin error (LAL-27)") {
            val session =
                Session
                    .create(
                        day = monday,
                        type = SessionType.TEMPO,
                        volume = SessionVolume.Distance(meters = 10000),
                        pace = Pace.Relativo(reference = RaceDistance.TEN_K, deltaSecondsPerKm = -10),
                    ).shouldBeRight()

            session.pace shouldBe Pace.Relativo(reference = RaceDistance.TEN_K, deltaSecondsPerKm = -10)
        }

        test("una sesion de descanso con ritmo relativo se rechaza") {
            val error =
                Session
                    .create(
                        day = monday,
                        type = SessionType.DESCANSO,
                        pace = Pace.Relativo(reference = RaceDistance.HALF_MARATHON, deltaSecondsPerKm = 15),
                    ).shouldBeLeft()

            error.shouldBeInstanceOf<PlanificacionError.InvalidInput>().field shouldBe "tipo"
        }

        test("una sesion de descanso sin volumen ni ritmo se acepta") {
            Session.create(day = monday, type = SessionType.DESCANSO).shouldBeRight()
        }

        test("una distancia de cero o negativa se rechaza") {
            val error =
                Session
                    .create(day = monday, type = SessionType.RODAJE, volume = SessionVolume.Distance(meters = 0))
                    .shouldBeLeft()

            error.shouldBeInstanceOf<PlanificacionError.InvalidInput>().field shouldBe "volumen"
        }

        test("una duracion de cero o negativa se rechaza") {
            val error =
                Session
                    .create(day = monday, type = SessionType.TEMPO, volume = SessionVolume.Duration(minutes = -5))
                    .shouldBeLeft()

            error.shouldBeInstanceOf<PlanificacionError.InvalidInput>().field shouldBe "volumen"
        }

        test("unas notas de mas de 1000 caracteres se rechazan") {
            val error =
                Session
                    .create(day = monday, type = SessionType.RODAJE, notes = "a".repeat(1001))
                    .shouldBeLeft()

            error.shouldBeInstanceOf<PlanificacionError.InvalidInput>().field shouldBe "notas"
        }

        test("unas notas de exactamente 1000 caracteres se aceptan") {
            Session.create(day = monday, type = SessionType.RODAJE, notes = "a".repeat(1000)).shouldBeRight()
        }
    })
