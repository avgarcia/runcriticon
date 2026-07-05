package com.runcriticon

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.infrastructure.persistence.UserEntity
import com.runcriticon.identidad.infrastructure.persistence.UserEntityRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.ClientHttpResponse
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

/**
 * Atributos de seguridad de las cookies (LAL-56, ADR-0003 D10): la cookie de sesión sale siempre
 * con Secure/HttpOnly/SameSite=Lax por configuración explícita (server.servlet.session.cookie.*),
 * y XSRF-TOKEN hereda Secure del esquema que el proxy de App Runner reenvía en X-Forwarded-Proto
 * (server.forward-headers-strategy, ADR-0006).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SessionCookieSecurityIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    private object LaxErrorHandler : DefaultResponseErrorHandler() {
        override fun hasError(response: ClientHttpResponse) = false
    }

    private val rest = RestTemplate().apply { errorHandler = LaxErrorHandler }

    @Autowired
    lateinit var usuarios: UserEntityRepository

    @Autowired
    lateinit var encoder: PasswordEncoder

    @BeforeEach
    fun sembrarAdmin() {
        if (usuarios.findByClubIdAndNormalizedEmail(clubId, EMAIL) != null) return
        val ahora = Instant.now()
        usuarios.save(
            UserEntity(
                id = UuidCreator.getTimeOrderedEpoch(),
                clubId = clubId,
                email = EMAIL,
                normalizedEmail = EMAIL,
                name = "Admin Cookies",
                role = "ADMIN",
                passwordHash = encoder.encode(PASSWORD),
                status = "ACTIVO",
                createdAt = ahora,
                modifiedAt = ahora,
            ),
        )
    }

    @Test
    fun `la cookie de sesion del login lleva Secure, HttpOnly y SameSite=Lax`() {
        val handshake = get("/api/sesion/actual")
        val xsrf = cookieValue(handshake, "XSRF-TOKEN")
        assertNotNull(xsrf, "El handshake debe emitir la cookie XSRF-TOKEN")

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers[HttpHeaders.COOKIE] = "XSRF-TOKEN=$xsrf"
        headers["X-XSRF-TOKEN"] = xsrf
        val login =
            rest.exchange(
                url("/api/sesion"),
                HttpMethod.POST,
                HttpEntity("""{"email":"$EMAIL","password":"$PASSWORD"}""", headers),
                String::class.java,
            )
        assertEquals(HttpStatus.OK, login.statusCode, login.body.orEmpty())

        val sessionCookie = setCookieDe(login, "SESSION")
        assertNotNull(sessionCookie, "El login debe emitir la cookie de sesión SESSION")
        assertTrue(sessionCookie!!.contains("Secure"), "Falta Secure en: $sessionCookie")
        assertTrue(sessionCookie.contains("HttpOnly"), "Falta HttpOnly en: $sessionCookie")
        assertTrue(sessionCookie.contains("SameSite=Lax"), "Falta SameSite=Lax en: $sessionCookie")
    }

    @Test
    fun `XSRF-TOKEN lleva Secure cuando el proxy reenvia X-Forwarded-Proto https`() {
        val handshake = get("/api/sesion/actual", forwardedProto = "https")
        val xsrfCookie = setCookieDe(handshake, "XSRF-TOKEN")
        assertNotNull(xsrfCookie, "El handshake debe emitir la cookie XSRF-TOKEN")
        assertTrue(xsrfCookie!!.contains("Secure"), "Falta Secure en: $xsrfCookie")
    }

    @Test
    fun `sin X-Forwarded-Proto la XSRF-TOKEN no lleva Secure`() {
        // Documenta que el Secure de XSRF-TOKEN depende del forward-header: si este test empieza a
        // fallar es que alguien lo fijó explícitamente (y el de arriba pasa a ser redundante).
        val handshake = get("/api/sesion/actual")
        val xsrfCookie = setCookieDe(handshake, "XSRF-TOKEN")
        assertNotNull(xsrfCookie, "El handshake debe emitir la cookie XSRF-TOKEN")
        assertFalse(xsrfCookie!!.contains("Secure"), "Secure inesperado en: $xsrfCookie")
    }

    private fun get(
        ruta: String,
        forwardedProto: String? = null,
    ): ResponseEntity<String> {
        val headers = HttpHeaders()
        forwardedProto?.let { headers["X-Forwarded-Proto"] = it }
        return rest.exchange(url(ruta), HttpMethod.GET, HttpEntity<String>(null, headers), String::class.java)
    }

    private fun url(ruta: String) = "http://localhost:$port$ruta"

    private fun setCookieDe(
        respuesta: ResponseEntity<*>,
        nombre: String,
    ): String? = respuesta.headers[HttpHeaders.SET_COOKIE]?.firstOrNull { it.startsWith("$nombre=") }

    private fun cookieValue(
        respuesta: ResponseEntity<*>,
        nombre: String,
    ): String? = setCookieDe(respuesta, nombre)?.substringBefore(";")?.substringAfter("=")

    companion object {
        private val clubId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        private const val EMAIL = "admin-cookies@runcriticon.local"
        private const val PASSWORD = "cookie-password-12345"

        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun propiedades(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            // TokenHasherImpl exige el secreto no vacío al arrancar (fail-fast, ADR-0003 D13).
            registry.add("runcriticon.security.token-hmac-secret") { "test-hmac-secret-not-prod" }
            registry.add("runcriticon.observability.userid-hash-salt") { "test-userid-hash-salt-not-prod" }
        }
    }
}
