package com.runcriticon

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.infrastructure.persistence.entities.UserEntity
import com.runcriticon.identidad.infrastructure.persistence.repositories.UserEntityRepository
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
 * Contrato de errores de framework (LAL-58, ADR-0012 D19): JSON malformado, UUID inválido en un
 * `@PathVariable` y rutas inexistentes devuelven `ErrorResponse` neutro `{code, field?, message}`
 * vía [com.runcriticon.identidad.infrastructure.rest.config.GlobalRestExceptionHandler], sin filtrar el
 * mensaje ni la clase de la excepción.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class GlobalRestExceptionHandlerIntegrationTest {
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
                name = "Admin Errores",
                role = "ADMIN",
                passwordHash = encoder.encode(PASSWORD),
                status = "ACTIVO",
                createdAt = ahora,
                modifiedAt = ahora,
            ),
        )
    }

    @Test
    fun `JSON malformado devuelve 400 INVALID_INPUT sin detalle de la excepcion`() {
        val xsrf = handshakeXsrf()
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers[HttpHeaders.COOKIE] = "XSRF-TOKEN=$xsrf"
        headers["X-XSRF-TOKEN"] = xsrf
        val respuesta =
            rest.exchange(
                url("/api/sesion"),
                HttpMethod.POST,
                HttpEntity("""{"email": "x@x.local", "password": """, headers),
                String::class.java,
            )

        assertEquals(HttpStatus.BAD_REQUEST, respuesta.statusCode, respuesta.body.orEmpty())
        assertBodyNeutro(respuesta, code = "INVALID_INPUT")
    }

    @Test
    fun `UUID invalido en un PathVariable devuelve 400 INVALID_INPUT con field`() {
        val session = login()
        val xsrf = handshakeXsrf(session)
        val headers = HttpHeaders()
        headers[HttpHeaders.COOKIE] = "SESSION=$session; XSRF-TOKEN=$xsrf"
        headers["X-XSRF-TOKEN"] = xsrf
        val respuesta =
            rest.exchange(
                url("/api/usuarios/no-es-un-uuid/desactivacion"),
                HttpMethod.POST,
                HttpEntity<String>(null, headers),
                String::class.java,
            )

        assertEquals(HttpStatus.BAD_REQUEST, respuesta.statusCode, respuesta.body.orEmpty())
        assertBodyNeutro(respuesta, code = "INVALID_INPUT")
        assertTrue(respuesta.body!!.contains("\"field\":\"id\""), "Falta field=id en: ${respuesta.body}")
    }

    @Test
    fun `ruta inexistente autenticada devuelve 404 NOT_FOUND estructurado`() {
        val session = login()
        val headers = HttpHeaders()
        headers[HttpHeaders.COOKIE] = "SESSION=$session"
        val respuesta =
            rest.exchange(url("/api/no-existe"), HttpMethod.GET, HttpEntity<String>(null, headers), String::class.java)

        assertEquals(HttpStatus.NOT_FOUND, respuesta.statusCode, respuesta.body.orEmpty())
        assertBodyNeutro(respuesta, code = "NOT_FOUND")
    }

    /** El body cumple el contrato D19 y no expone clase ni mensaje de la excepción de framework. */
    private fun assertBodyNeutro(
        respuesta: ResponseEntity<String>,
        code: String,
    ) {
        val body = respuesta.body
        assertNotNull(body, "El error debe llevar body estructurado")
        assertTrue(body!!.contains("\"code\":\"$code\""), "Falta code=$code en: $body")
        assertFalse(body.contains("Exception"), "El body expone la excepción: $body")
        assertFalse(body.contains("jackson", ignoreCase = true), "El body expone el parser: $body")
    }

    private fun handshakeXsrf(session: String? = null): String {
        val headers = HttpHeaders()
        session?.let { headers[HttpHeaders.COOKIE] = "SESSION=$it" }
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
                HttpEntity("""{"email":"$EMAIL","password":"$PASSWORD"}""", headers),
                String::class.java,
            )
        assertEquals(HttpStatus.OK, respuesta.statusCode, respuesta.body.orEmpty())
        val session = cookieValue(respuesta, "SESSION")
        assertNotNull(session, "El login debe emitir la cookie SESSION")
        return session!!
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

    companion object {
        private val clubId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        private const val EMAIL = "admin-errores@runcriticon.local"
        private const val PASSWORD = "errores-password-12345"

        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun propiedades(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("runcriticon.security.token-hmac-secret") { "test-hmac-secret-not-prod" }
            registry.add("runcriticon.observability.userid-hash-salt") { "test-userid-hash-salt-not-prod" }
        }
    }
}
