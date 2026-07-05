package com.runcriticon

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.infrastructure.persistence.UserEntity
import com.runcriticon.identidad.infrastructure.persistence.UserEntityRepository
import com.runcriticon.testing.MutableClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.ClientHttpResponse
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Integración de los timeouts de sesión de ADR-0003 D10 (LAL-57) por HTTP real sobre Postgres
 * (Testcontainers): la sesión nace con la expiración deslizante de 30 días (`MAX_INACTIVE_INTERVAL`)
 * y, superado el tope absoluto de 90 días desde la autenticación, se invalida y responde 401. El
 * tiempo se controla con el reloj mutable de la aplicación (el bean real es
 * `@ConditionalOnMissingBean`), sin esperas reales.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SessionTimeoutIntegrationTest {
    @TestConfiguration(proxyBeanMethods = false)
    class MutableClockConfig {
        @Bean
        @Primary
        fun mutableClock(): MutableClock = MutableClock(Instant.parse("2026-07-05T10:00:00Z"))
    }

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

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @Autowired
    lateinit var clock: MutableClock

    /** Cookie jar: acumula y actualiza las cookies entre peticiones, como un navegador. */
    private val cookies = mutableMapOf<String, String>()

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
                name = "Admin Timeout",
                role = "ADMIN",
                passwordHash = encoder.encode(PASSWORD),
                status = "ACTIVO",
                createdAt = ahora,
                modifiedAt = ahora,
            ),
        )
    }

    @Test
    fun `la sesion nace con la expiracion deslizante de 30 dias`() {
        login()

        val maxInactive =
            jdbc.queryForObject(
                "SELECT MAX_INACTIVE_INTERVAL FROM SPRING_SESSION WHERE SESSION_ID = ?",
                Int::class.javaObjectType,
                sessionId(),
            )

        assertEquals(Duration.ofDays(30).seconds.toInt(), maxInactive)
    }

    @Test
    fun `superado el tope absoluto de 90 dias la sesion se invalida y responde 401`() {
        login()
        assertEquals(HttpStatus.OK, get("/api/sesion/actual").statusCode)
        val sessionId = sessionId()

        clock.instant = clock.instant.plus(Duration.ofDays(91))

        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/sesion/actual").statusCode)
        val filas =
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE SESSION_ID = ?",
                Int::class.javaObjectType,
                sessionId,
            )
        assertEquals(0, filas, "La sesión caducada debe invalidarse (fila borrada de SPRING_SESSION)")
    }

    /** Handshake CSRF (la cookie XSRF-TOKEN llega en el 401 anónimo) + login con contraseña. */
    private fun login() {
        val handshake = get("/api/sesion/actual")
        assertEquals(HttpStatus.UNAUTHORIZED, handshake.statusCode)
        val login = postJson("/api/sesion", """{"email":"$EMAIL","password":"$PASSWORD"}""")
        assertEquals(HttpStatus.OK, login.statusCode, login.body.orEmpty())
    }

    /** La cookie SESSION lleva el id en Base64 (DefaultCookieSerializer); en la tabla va en claro. */
    private fun sessionId(): String = String(Base64.getDecoder().decode(cookies.getValue("SESSION")))

    private fun get(ruta: String): ResponseEntity<String> = intercambiar(ruta, HttpMethod.GET, null)

    private fun postJson(
        ruta: String,
        cuerpo: String,
    ): ResponseEntity<String> = intercambiar(ruta, HttpMethod.POST, cuerpo)

    private fun intercambiar(
        ruta: String,
        metodo: HttpMethod,
        cuerpo: String?,
    ): ResponseEntity<String> {
        val headers = HttpHeaders()
        if (cookies.isNotEmpty()) {
            headers[HttpHeaders.COOKIE] = cookies.entries.joinToString("; ") { (nombre, valor) -> "$nombre=$valor" }
        }
        if (metodo != HttpMethod.GET) {
            headers.contentType = MediaType.APPLICATION_JSON
            cookies["XSRF-TOKEN"]?.let { headers["X-XSRF-TOKEN"] = it }
        }
        val respuesta =
            rest.exchange(
                "http://localhost:$port$ruta",
                metodo,
                HttpEntity(cuerpo, headers),
                String::class.java,
            )
        acumularCookies(respuesta)
        return respuesta
    }

    private fun acumularCookies(respuesta: ResponseEntity<*>) {
        respuesta.headers[HttpHeaders.SET_COOKIE]?.forEach { setCookie ->
            val par = setCookie.substringBefore(";")
            val nombre = par.substringBefore("=")
            val valor = par.substringAfter("=")
            if (valor.isBlank()) cookies.remove(nombre) else cookies[nombre] = valor
        }
    }

    companion object {
        private val clubId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        private const val EMAIL = "admin.timeout@runcriticon.local"
        private const val PASSWORD = "timeout-password-12345"

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
