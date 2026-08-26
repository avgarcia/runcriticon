package com.runcriticon.identidad.contracts

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.model.Request
import com.atlassian.oai.validator.model.SimpleResponse
import com.atlassian.oai.validator.report.ValidationReport
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.domain.consent.ConsentText
import com.runcriticon.identidad.infrastructure.persistence.entities.UserEntity
import com.runcriticon.identidad.infrastructure.persistence.repositories.UserEntityRepository
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.jdbc.core.JdbcTemplate
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
 * Contrato REST runtime contra `api/openapi.yaml` para `GET`/`POST`/`DELETE /me/consentimiento`
 * (LAL-128). Mismo patrón que [ClubOpenApiContractTest]: backend arrancado con Testcontainers, login
 * real por HTTP, sin mocks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class MiConsentimientoOpenApiContractTest {
    @LocalServerPort
    private var port: Int = 0

    private object LaxErrorHandler : DefaultResponseErrorHandler() {
        override fun hasError(response: ClientHttpResponse) = false
    }

    private val rest = RestTemplate(JdkClientHttpRequestFactory()).apply { errorHandler = LaxErrorHandler }
    private val cookies = mutableMapOf<String, String>()
    private val json = ObjectMapper()

    private val validator: OpenApiInteractionValidator = buildValidator()

    @Autowired
    lateinit var usuarios: UserEntityRepository

    @Autowired
    lateinit var encoder: PasswordEncoder

    @Autowired
    lateinit var jdbc: JdbcTemplate

    @BeforeEach
    fun sembrarAlumno() {
        if (usuarios.findByClubIdAndNormalizedEmail(clubId, EMAIL) == null) {
            val ahora = Instant.now()
            usuarios.save(
                UserEntity(
                    id = alumnoId,
                    clubId = clubId,
                    email = EMAIL,
                    normalizedEmail = EMAIL,
                    name = "Alumno Consentimiento Contract",
                    role = "ALUMNO",
                    passwordHash = encoder.encode(PASSWORD),
                    status = "ACTIVO",
                    createdAt = ahora,
                    modifiedAt = ahora,
                ),
            )
        }
        // Cada test parte de PENDIENTE: sin esto, una fila de un test anterior contaminaría el estado.
        jdbc.update("DELETE FROM identidad.consentimiento WHERE usuario_id = ?", alumnoId)
    }

    @Test
    fun `sin ninguna concesion, GET devuelve PENDIENTE y cumple el contrato`() {
        autenticar()

        val respuesta = get("/api/me/consentimiento")

        assertEquals(HttpStatus.OK, respuesta.statusCode, respuesta.body.orEmpty())
        assertContract(Request.Method.GET, "/me/consentimiento", HttpStatus.OK, respuesta.body)
        assertEquals("PENDIENTE", json.readTree(respuesta.body).get("estado").asText())
    }

    @Test
    fun `POST concede el consentimiento y GET refleja VIGENTE, ambos cumplen el contrato`() {
        autenticar()

        val post = postJson("/api/me/consentimiento", """{"versionConsentimiento":"${ConsentText.CURRENT_VERSION}"}""")
        assertEquals(HttpStatus.OK, post.statusCode, post.body.orEmpty())
        assertContract(Request.Method.POST, "/me/consentimiento", HttpStatus.OK, post.body)
        assertEquals("VIGENTE", json.readTree(post.body).get("estado").asText())

        val get = get("/api/me/consentimiento")
        assertEquals("VIGENTE", json.readTree(get.body).get("estado").asText())
    }

    @Test
    fun `POST con una version obsoleta da 409 y cumple el contrato`() {
        autenticar()

        val post = postJson("/api/me/consentimiento", """{"versionConsentimiento":"v2000-01-01"}""")

        assertEquals(HttpStatus.CONFLICT, post.statusCode, post.body.orEmpty())
        assertContract(Request.Method.POST, "/me/consentimiento", HttpStatus.CONFLICT, post.body)
        assertEquals("VERSION_CONSENTIMIENTO_OBSOLETA", json.readTree(post.body).get("code").asText())
    }

    @Test
    fun `DELETE sin ninguna concesion previa da 409 y cumple el contrato`() {
        autenticar()

        val delete = delete("/api/me/consentimiento")

        assertEquals(HttpStatus.CONFLICT, delete.statusCode, delete.body.orEmpty())
        assertContract(Request.Method.DELETE, "/me/consentimiento", HttpStatus.CONFLICT, delete.body)
    }

    @Test
    fun `conceder y revocar deja REVOCADO, y cumple el contrato`() {
        autenticar()
        postJson("/api/me/consentimiento", """{"versionConsentimiento":"${ConsentText.CURRENT_VERSION}"}""")

        val delete = delete("/api/me/consentimiento")

        assertEquals(HttpStatus.OK, delete.statusCode, delete.body.orEmpty())
        assertContract(Request.Method.DELETE, "/me/consentimiento", HttpStatus.OK, delete.body)
        assertEquals("REVOCADO", json.readTree(delete.body).get("estado").asText())
    }

    private fun autenticar() {
        get("/api/sesion/actual") // handshake CSRF
        val login = postJson("/api/sesion", """{"email":"$EMAIL","password":"$PASSWORD"}""")
        assertEquals(HttpStatus.OK, login.statusCode, login.body.orEmpty())
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
        val report = validator.validateResponse(specPath, method, builder.build())
        val errores = report.messages.filter { it.level == ValidationReport.Level.ERROR }
        org.junit.jupiter.api.Assertions.assertTrue(
            errores.isEmpty(),
            "Respuesta $method $specPath ($status) no cumple api/openapi.yaml:\n" +
                errores.joinToString("\n") { "- ${it.message}" },
        )
    }

    private fun get(ruta: String): ResponseEntity<String> = intercambiar(ruta, HttpMethod.GET, null)

    private fun postJson(
        ruta: String,
        cuerpo: String?,
    ): ResponseEntity<String> = intercambiar(ruta, HttpMethod.POST, cuerpo)

    private fun delete(ruta: String): ResponseEntity<String> = intercambiar(ruta, HttpMethod.DELETE, null)

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
            rest.exchange("http://localhost:$port$ruta", metodo, HttpEntity(cuerpo, headers), String::class.java)
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
        private val alumnoId: UUID = UuidCreator.getTimeOrderedEpoch()
        private const val EMAIL = "alumno-consentimiento-contract@runcriticon.local"
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
