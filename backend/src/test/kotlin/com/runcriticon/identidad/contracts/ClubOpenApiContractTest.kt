package com.runcriticon.identidad.contracts

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.model.Request
import com.atlassian.oai.validator.model.SimpleResponse
import com.atlassian.oai.validator.report.ValidationReport
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.infrastructure.persistence.entities.UserEntity
import com.runcriticon.identidad.infrastructure.persistence.repositories.UserEntityRepository
import org.junit.jupiter.api.Assertions.assertEquals
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
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID

/**
 * Contrato REST runtime contra `api/openapi.yaml` (ADR-0001 D10) para `GET`/`PATCH /api/club`
 * (ADR-0006 D30): valida que las respuestas reales del backend arrancado (Testcontainers, sin mocks)
 * cumplen la spec. Mismo patrón que [SessionOpenApiContractTest].
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ClubOpenApiContractTest {
    @LocalServerPort
    private var port: Int = 0

    private object LaxErrorHandler : DefaultResponseErrorHandler() {
        override fun hasError(response: ClientHttpResponse) = false
    }

    private val rest = RestTemplate().apply { errorHandler = LaxErrorHandler }
    private val cookies = mutableMapOf<String, String>()

    private val validator: OpenApiInteractionValidator = buildValidator()

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
                name = "Admin Club Contract",
                role = "ADMIN",
                passwordHash = encoder.encode(PASSWORD),
                status = "ACTIVO",
                createdAt = ahora,
                modifiedAt = ahora,
            ),
        )
    }

    @Test
    fun `GET y PATCH club cumplen el contrato OpenAPI`() {
        get("/api/sesion/actual") // handshake CSRF
        val login = postJson("/api/sesion", """{"email":"$EMAIL","password":"$PASSWORD"}""")
        assertEquals(HttpStatus.OK, login.statusCode, login.body.orEmpty())

        val get = get("/api/club")
        assertEquals(HttpStatus.OK, get.statusCode, get.body.orEmpty())
        assertContract(Request.Method.GET, "/club", HttpStatus.OK, get.body)

        val patch = patchJson("/api/club", """{"nombre":"Club Runcriticon Contract"}""")
        assertEquals(HttpStatus.OK, patch.statusCode, patch.body.orEmpty())
        assertContract(Request.Method.PATCH, "/club", HttpStatus.OK, patch.body)
    }

    private fun assertContract(
        method: Request.Method,
        specPath: String,
        status: HttpStatus,
        body: String?,
    ) {
        val builder = SimpleResponse.Builder(status.value())
        if (body != null) {
            builder.withBody(body).withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        }
        val response = builder.build()
        val report = validator.validateResponse(specPath, method, response)
        val errores = report.messages.filter { it.level == ValidationReport.Level.ERROR }
        assertTrue(
            errores.isEmpty(),
            "Respuesta $method $specPath ($status) no cumple api/openapi.yaml:\n" +
                errores.joinToString("\n") { "- ${it.message}" },
        )
    }

    private fun get(ruta: String): ResponseEntity<String> = intercambiar(ruta, HttpMethod.GET, null)

    private fun postJson(
        ruta: String,
        cuerpo: String,
    ): ResponseEntity<String> = intercambiar(ruta, HttpMethod.POST, cuerpo)

    private fun patchJson(
        ruta: String,
        cuerpo: String,
    ): ResponseEntity<String> = intercambiar(ruta, HttpMethod.PATCH, cuerpo)

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
        private const val EMAIL = "admin-club-contract@runcriticon.local"
        private const val PASSWORD = "contract-test-password-12345"

        private fun buildValidator(): OpenApiInteractionValidator {
            val specPath = Paths.get("../api/openapi.yaml").toAbsolutePath().normalize()
            return OpenApiInteractionValidator.createFor(specPath.toString()).build()
        }

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
