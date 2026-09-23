package com.runcriticon.testing

import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.security.core.context.SecurityContextHolder

class TestPrincipalsTest :
    FunSpec({
        test("TestClubs.newClub genera un club distinto cada vez") {
            TestClubs.newClub() shouldNotBe TestClubs.newClub()
        }

        test("PrincipalBuilder por defecto usa un club nuevo y rol ALUMNO") {
            val p1 = PrincipalBuilder().build()
            val p2 = PrincipalBuilder().build()

            p1.role shouldBe Role.ALUMNO
            p1.clubId shouldNotBe p2.clubId
        }

        test("TestPrincipals.twoCoachesSameClub devuelve dos entrenadores del mismo club, distinto userId") {
            val (a, b) = TestPrincipals.twoCoachesSameClub()

            a.role shouldBe Role.ENTRENADOR
            b.role shouldBe Role.ENTRENADOR
            a.clubId shouldBe b.clubId
            a.userId shouldNotBe b.userId
        }

        test("TestPrincipals.sameRoleInTwoClubs devuelve el mismo rol en clubes distintos") {
            val (a, b) = TestPrincipals.sameRoleInTwoClubs(Role.ALUMNO)

            a.role shouldBe Role.ALUMNO
            b.role shouldBe Role.ALUMNO
            a.clubId shouldNotBe b.clubId
        }

        test("TestPrincipalContext.withPrincipal autentica durante el bloque y limpia el contexto después") {
            val principal = PrincipalBuilder().coach().build()

            val authenticatedDuring =
                TestPrincipalContext.withPrincipal(principal) {
                    SecurityContextHolder.getContext().authentication?.principal
                }

            authenticatedDuring shouldBe principal
            SecurityContextHolder.getContext().authentication shouldBe null
        }

        test("TestPrincipalContext.withPrincipal limpia el contexto incluso si el bloque lanza") {
            val principal = PrincipalBuilder().admin().build()

            runCatching {
                TestPrincipalContext.withPrincipal(principal) {
                    error("boom")
                }
            }

            SecurityContextHolder.getContext().authentication shouldBe null
        }
    })
