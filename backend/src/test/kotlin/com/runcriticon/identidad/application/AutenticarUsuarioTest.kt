package com.runcriticon.identidad.application

import com.runcriticon.identidad.domain.AutenticacionError
import com.runcriticon.identidad.domain.Email
import com.runcriticon.identidad.domain.EstadoUsuario
import com.runcriticon.identidad.domain.Usuario
import com.runcriticon.identidad.domain.UsuarioId
import com.runcriticon.shared.autorizacion.Rol
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.UUID

class AutenticarUsuarioTest :
    FunSpec({
        val club = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val repo = mockk<RepositorioDeUsuarios>()
        val hash = mockk<HashDePassword>()
        val caso = AutenticarUsuario(repo, hash)

        fun usuario(
            estado: EstadoUsuario = EstadoUsuario.ACTIVO,
            passwordHash: String? = "hash-guardado",
        ) = Usuario(
            id = UsuarioId.nuevo(),
            clubId = club,
            email = Email.de("alumno@club.local"),
            nombre = "Alumno",
            rol = Rol.ALUMNO,
            passwordHash = passwordHash,
            estado = estado,
        )

        test("usuario inexistente devuelve CredencialesInvalidas") {
            every { repo.buscarPorEmail(any(), any()) } returns null
            caso
                .ejecutar(club, "x@club.local", "secreta")
                .shouldBeLeft(AutenticacionError.CredencialesInvalidas)
        }

        test("cuenta no activa devuelve CuentaNoActiva") {
            every { repo.buscarPorEmail(any(), any()) } returns usuario(estado = EstadoUsuario.INVITADO)
            caso
                .ejecutar(club, "x@club.local", "secreta")
                .shouldBeLeft(AutenticacionError.CuentaNoActiva)
        }

        test("usuario solo-magic-link (sin contraseña) devuelve CredencialesInvalidas") {
            every { repo.buscarPorEmail(any(), any()) } returns usuario(passwordHash = null)
            caso
                .ejecutar(club, "x@club.local", "secreta")
                .shouldBeLeft(AutenticacionError.CredencialesInvalidas)
        }

        test("contraseña incorrecta devuelve CredencialesInvalidas") {
            every { repo.buscarPorEmail(any(), any()) } returns usuario()
            every { hash.coincide(any(), any()) } returns false
            caso
                .ejecutar(club, "x@club.local", "incorrecta")
                .shouldBeLeft(AutenticacionError.CredencialesInvalidas)
        }

        test("credenciales correctas devuelven el Principal del usuario") {
            val esperado = usuario()
            every { repo.buscarPorEmail(any(), any()) } returns esperado
            every { hash.coincide(any(), any()) } returns true
            val principal = caso.ejecutar(club, "alumno@club.local", "correcta").shouldBeRight()
            principal.userId shouldBe esperado.id.valor
            principal.clubId shouldBe club
            principal.rol shouldBe Rol.ALUMNO
        }
    })
