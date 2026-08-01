package com.runcriticon.identidad.infrastructure.rest

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.infrastructure.persistence.entities.UserEntity
import com.runcriticon.identidad.infrastructure.persistence.repositories.UserEntityRepository
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.ClientHttpResponse
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.UUID

/**
 * El endpoint de supresión por HTTP real, no invocando el caso de uso: es lo único que comprueba que el verbo `DELETE`
 * atraviesa la configuración de seguridad y la protección CSRF. Un `DELETE` rechazado por CSRF o por la cadena de
 * filtros devolvería 403 en producción con toda la lógica de negocio en verde.
 */
class UserDeletionEndpointIntegrationTest : IntegrationTestBase() {
    @LocalServerPort private var port: Int = 0

    @Autowired private lateinit var usuarios: UserEntityRepository

    @Autowired private lateinit var encoder: PasswordEncoder

    private object LaxErrorHandler : DefaultResponseErrorHandler() {
        override fun hasError(response: ClientHttpResponse) = false
    }

    private val rest = RestTemplate().apply { errorHandler = LaxErrorHandler }

    @Test
    fun `el admin elimina a un alumno y la fila desaparece`() {
        sembrarAdmin()
        val alumno = sembrarUsuario("ALUMNO")
        val sesion = login()

        val respuesta = eliminar(alumno, sesion)

        respuesta.statusCode shouldBe HttpStatus.NO_CONTENT
        usuarios.findById(alumno).isPresent shouldBe false
    }

    @Test
    fun `repetir la eliminacion devuelve 404 porque el recurso ya no existe`() {
        sembrarAdmin()
        val alumno = sembrarUsuario("ALUMNO")
        val sesion = login()
        eliminar(alumno, sesion).statusCode shouldBe HttpStatus.NO_CONTENT

        eliminar(alumno, sesion).statusCode shouldBe HttpStatus.NOT_FOUND
    }

    @Test
    fun `sin el token CSRF la peticion se rechaza y el alumno sigue existiendo`() {
        sembrarAdmin()
        val alumno = sembrarUsuario("ALUMNO")
        val sesion = login()

        val headers = HttpHeaders()
        headers[HttpHeaders.COOKIE] = "SESSION=$sesion"
        val respuesta =
            rest.exchange(
                url("/api/usuarios/$alumno"),
                HttpMethod.DELETE,
                HttpEntity<String>(null, headers),
                String::class.java,
            )

        respuesta.statusCode shouldBe HttpStatus.FORBIDDEN
        usuarios.findById(alumno).isPresent shouldBe true
    }

    @Test
    fun `sin sesion la peticion se rechaza`() {
        sembrarAdmin()
        val alumno = sembrarUsuario("ALUMNO")

        val respuesta =
            rest.exchange(
                url("/api/usuarios/$alumno"),
                HttpMethod.DELETE,
                HttpEntity<String>(null, HttpHeaders()),
                String::class.java,
            )

        respuesta.statusCode shouldBe HttpStatus.FORBIDDEN
        usuarios.findById(alumno).isPresent shouldBe true
    }

    private fun eliminar(
        userId: UUID,
        sesion: String,
    ): ResponseEntity<String> {
        val xsrf = handshakeXsrf(sesion)
        val headers = HttpHeaders()
        headers[HttpHeaders.COOKIE] = "SESSION=$sesion; XSRF-TOKEN=$xsrf"
        headers["X-XSRF-TOKEN"] = xsrf
        return rest.exchange(
            url("/api/usuarios/$userId"),
            HttpMethod.DELETE,
            HttpEntity<String>(null, headers),
            String::class.java,
        )
    }

    private fun sembrarAdmin() {
        if (usuarios.findByClubIdAndNormalizedEmail(CLUB_ID, EMAIL_ADMIN) != null) return
        val ahora = Instant.now()
        usuarios.save(
            UserEntity(
                id = UuidCreator.getTimeOrderedEpoch(),
                clubId = CLUB_ID,
                email = EMAIL_ADMIN,
                normalizedEmail = EMAIL_ADMIN,
                name = "Admin Supresion",
                role = "ADMIN",
                passwordHash = encoder.encode(PASSWORD),
                status = "ACTIVO",
                createdAt = ahora,
                modifiedAt = ahora,
            ),
        )
    }

    private fun sembrarUsuario(rol: String): UUID {
        val id = UuidCreator.getTimeOrderedEpoch()
        val ahora = Instant.now()
        val email = "borrable-$id@runcriticon.local"
        usuarios.save(
            UserEntity(
                id = id,
                clubId = CLUB_ID,
                email = email,
                normalizedEmail = email,
                name = "Persona Borrable",
                role = rol,
                passwordHash = encoder.encode(PASSWORD),
                status = "ACTIVO",
                createdAt = ahora,
                modifiedAt = ahora,
            ),
        )
        return id
    }

    private fun handshakeXsrf(sesion: String? = null): String {
        val headers = HttpHeaders()
        sesion?.let { headers[HttpHeaders.COOKIE] = "SESSION=$it" }
        val respuesta =
            rest.exchange(
                url("/api/sesion/actual"),
                HttpMethod.GET,
                HttpEntity<String>(null, headers),
                String::class.java,
            )
        val xsrf = cookieValue(respuesta, "XSRF-TOKEN")
        assertNotNull(xsrf, "El handshake debe emitir la cookie XSRF-TOKEN")
        return xsrf!!
    }

    private fun login(): String {
        val xsrf = handshakeXsrf()
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers[HttpHeaders.COOKIE] = "XSRF-TOKEN=$xsrf"
        headers["X-XSRF-TOKEN"] = xsrf
        val respuesta =
            rest.exchange(
                url("/api/sesion"),
                HttpMethod.POST,
                HttpEntity("""{"email":"$EMAIL_ADMIN","password":"$PASSWORD"}""", headers),
                String::class.java,
            )
        respuesta.statusCode shouldBe HttpStatus.OK
        val sesion = cookieValue(respuesta, "SESSION")
        assertNotNull(sesion, "El login debe emitir la cookie SESSION")
        return sesion!!
    }

    private fun url(ruta: String) = "http://localhost:$port$ruta"

    private fun cookieValue(
        respuesta: ResponseEntity<*>,
        nombre: String,
    ): String? =
        respuesta.headers[HttpHeaders.SET_COOKIE]
            ?.firstOrNull { it.startsWith("$nombre=") }
            ?.substringBefore(";")
            ?.substringAfter("=")

    private companion object {
        val CLUB_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        const val EMAIL_ADMIN = "admin-supresion@runcriticon.local"
        const val PASSWORD = "supresion-password-12345"
    }
}
