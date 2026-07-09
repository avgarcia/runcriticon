package com.runcriticon.identidad.application.usecases

import arrow.core.left
import arrow.core.right
import com.runcriticon.identidad.application.InvitationIssuer
import com.runcriticon.identidad.domain.errors.IdentidadError
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.autorizacion.model.ClubId
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

/**
 * La orquestación (rotación de token, email, auditoría) y el check de rol se prueban una sola vez en
 * [com.runcriticon.identidad.application.InvitationIssuerTest]. Este cascarón solo prueba lo que le
 * es propio: el check de matriz con `Resource.STUDENT` y que delega con `expectedRole = ALUMNO`.
 */
class ResendStudentInvitationTest :
    FunSpec({
        val club = ClubId.of(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val admin = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ADMIN)
        val coach = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ENTRENADOR)
        val studentId = UserId.new()

        val invitationIssuer = mockk<InvitationIssuer>()
        val useCase = ResendStudentInvitation(invitationIssuer)

        val reissuedStudent =
            User(
                id = studentId,
                clubId = club,
                email = Email.of("marta@club.local"),
                name = "Marta",
                role = Role.ALUMNO,
                passwordHash = null,
                status = UserStatus.INVITADO,
            )

        beforeTest {
            clearMocks(invitationIssuer)
            every { invitationIssuer.reissueFor(any(), any(), any()) } returns reissuedStudent.right()
        }

        test("admin reenvía: delega en InvitationIssuer.reissueFor con expectedRole ALUMNO") {
            useCase.execute(admin, studentId).shouldBeRight()

            verify { invitationIssuer.reissueFor(admin, studentId, Role.ALUMNO) }
        }

        test("un entrenador también puede reenviar (delegación, ADR-0003 D3)") {
            useCase.execute(coach, studentId).shouldBeRight()

            verify { invitationIssuer.reissueFor(coach, studentId, Role.ALUMNO) }
        }

        test("propaga el Left de InvitationIssuer tal cual (incluido NotFound por id de entrenador)") {
            every { invitationIssuer.reissueFor(any(), any(), any()) } returns IdentidadError.NotFound.left()

            useCase.execute(admin, studentId).shouldBeLeft(IdentidadError.NotFound)
        }

        test("un ALUMNO no puede reenviar: Forbidden y no llega a InvitationIssuer") {
            val student = Principal(userId = UUID.randomUUID(), clubId = club.value, role = Role.ALUMNO)

            useCase.execute(student, studentId).shouldBeLeft(IdentidadError.Forbidden)

            verify(exactly = 0) { invitationIssuer.reissueFor(any(), any(), any()) }
        }
    })
