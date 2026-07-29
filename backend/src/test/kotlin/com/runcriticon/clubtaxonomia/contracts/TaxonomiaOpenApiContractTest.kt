package com.runcriticon.clubtaxonomia.contracts

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.model.Request
import com.atlassian.oai.validator.model.SimpleResponse
import com.atlassian.oai.validator.report.ValidationReport
import com.fasterxml.jackson.databind.ObjectMapper
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
import org.springframework.http.client.JdkClientHttpRequestFactory
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
 * Contrato REST runtime contra `api/openapi.yaml` para los 9 endpoints de la taxonomía. Mismo patrón que
 * `ClubOpenApiContractTest`: backend arrancado con Testcontainers, login real por HTTP, sin mocks.
 *
 * Recorre las operaciones encadenadas porque el estado de una alimenta a la siguiente, y cubre expresamente los dos
 * `DELETE`: son los primeros verbos DELETE de toda la API, así que ni el enrutamiento ni el CSRF sobre ellos estaban
 * ejercitados en ningún sitio.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TaxonomiaOpenApiContractTest {
    @LocalServerPort
    private var port: Int = 0

    private object LaxErrorHandler : DefaultResponseErrorHandler() {
        override fun hasError(response: ClientHttpResponse) = false
    }

    /** `JdkClientHttpRequestFactory` porque `HttpURLConnection` rechaza PATCH con `Invalid HTTP method`. */
    private val rest = RestTemplate(JdkClientHttpRequestFactory()).apply { errorHandler = LaxErrorHandler }
    private val cookies = mutableMapOf<String, String>()
    private val json = ObjectMapper()

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
                name = "Admin Taxonomia Contract",
                role = "ADMIN",
                passwordHash = encoder.encode(PASSWORD),
                status = "ACTIVO",
                createdAt = ahora,
                modifiedAt = ahora,
            ),
        )
    }

    @Test
    fun `las nueve operaciones de la taxonomia cumplen el contrato OpenAPI`() {
        autenticar()

        val creado = postJson("/api/taxonomia/tags", """{"nombre":"Nivel contrato"}""")
        assertEquals(HttpStatus.CREATED, creado.statusCode, creado.body.orEmpty())
        assertContract(Request.Method.POST, "/taxonomia/tags", HttpStatus.CREATED, creado.body)
        val tagId = idDe(creado)

        val renombrado = patchJson("/api/taxonomia/tags/$tagId", """{"nombre":"Nivel contrato editado"}""")
        assertEquals(HttpStatus.OK, renombrado.statusCode, renombrado.body.orEmpty())
        assertContract(Request.Method.PATCH, "/taxonomia/tags/{tagId}", HttpStatus.OK, renombrado.body)

        val valorCreado = postJson("/api/taxonomia/tags/$tagId/valores", """{"valor":"Principiante"}""")
        assertEquals(HttpStatus.CREATED, valorCreado.statusCode, valorCreado.body.orEmpty())
        assertContract(Request.Method.POST, "/taxonomia/tags/{tagId}/valores", HttpStatus.CREATED, valorCreado.body)
        val valorId = idDe(valorCreado)

        val valorRenombrado = patchJson("/api/taxonomia/valores/$valorId", """{"valor":"Iniciación"}""")
        assertEquals(HttpStatus.OK, valorRenombrado.statusCode, valorRenombrado.body.orEmpty())
        assertContract(Request.Method.PATCH, "/taxonomia/valores/{valorId}", HttpStatus.OK, valorRenombrado.body)

        val valorArchivado = postJson("/api/taxonomia/valores/$valorId/archivado", null)
        assertEquals(HttpStatus.OK, valorArchivado.statusCode, valorArchivado.body.orEmpty())
        assertContract(
            Request.Method.POST,
            "/taxonomia/valores/{valorId}/archivado",
            HttpStatus.OK,
            valorArchivado.body,
        )

        val valorReactivado = delete("/api/taxonomia/valores/$valorId/archivado")
        assertEquals(HttpStatus.OK, valorReactivado.statusCode, valorReactivado.body.orEmpty())
        assertContract(
            Request.Method.DELETE,
            "/taxonomia/valores/{valorId}/archivado",
            HttpStatus.OK,
            valorReactivado.body,
        )

        val archivado = postJson("/api/taxonomia/tags/$tagId/archivado", null)
        assertEquals(HttpStatus.OK, archivado.statusCode, archivado.body.orEmpty())
        assertContract(Request.Method.POST, "/taxonomia/tags/{tagId}/archivado", HttpStatus.OK, archivado.body)

        val reactivado = delete("/api/taxonomia/tags/$tagId/archivado")
        assertEquals(HttpStatus.OK, reactivado.statusCode, reactivado.body.orEmpty())
        assertContract(Request.Method.DELETE, "/taxonomia/tags/{tagId}/archivado", HttpStatus.OK, reactivado.body)

        val listado = get("/api/taxonomia")
        assertEquals(HttpStatus.OK, listado.statusCode, listado.body.orEmpty())
        assertContract(Request.Method.GET, "/taxonomia", HttpStatus.OK, listado.body)
    }

    @Test
    fun `el 404 de un eje inexistente cumple el contrato`() {
        autenticar()

        val respuesta = patchJson("/api/taxonomia/tags/${UUID.randomUUID()}", """{"nombre":"No existe"}""")

        assertEquals(HttpStatus.NOT_FOUND, respuesta.statusCode, respuesta.body.orEmpty())
        assertEquals("TAG_KEY_NOT_FOUND", json.readTree(respuesta.body).get("code").asText())
        assertContract(Request.Method.PATCH, "/taxonomia/tags/{tagId}", HttpStatus.NOT_FOUND, respuesta.body)
    }

    @Test
    fun `el 409 de un nombre duplicado cumple el contrato`() {
        autenticar()
        postJson("/api/taxonomia/tags", """{"nombre":"Duplicado contrato"}""")

        val repetido = postJson("/api/taxonomia/tags", """{"nombre":"  duplicado CONTRATO "}""")

        assertEquals(HttpStatus.CONFLICT, repetido.statusCode, repetido.body.orEmpty())
        val cuerpo = json.readTree(repetido.body)
        assertEquals("DUPLICATE_LABEL", cuerpo.get("code").asText())
        assertEquals("nombre", cuerpo.get("field").asText())
        assertContract(Request.Method.POST, "/taxonomia/tags", HttpStatus.CONFLICT, repetido.body)
    }

    private fun autenticar() {
        get("/api/sesion/actual") // handshake CSRF
        val login = postJson("/api/sesion", """{"email":"$EMAIL","password":"$PASSWORD"}""")
        assertEquals(HttpStatus.OK, login.statusCode, login.body.orEmpty())
    }

    private fun idDe(respuesta: ResponseEntity<String>): String = json.readTree(respuesta.body).get("id").asText()

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
        assertTrue(
            errores.isEmpty(),
            "Respuesta $method $specPath ($status) no cumple api/openapi.yaml:\n" +
                errores.joinToString("\n") { "- ${it.message}" },
        )
    }

    private fun get(ruta: String): ResponseEntity<String> = intercambiar(ruta, HttpMethod.GET, null)

    private fun delete(ruta: String): ResponseEntity<String> = intercambiar(ruta, HttpMethod.DELETE, null)

    private fun postJson(
        ruta: String,
        cuerpo: String?,
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
        private const val EMAIL = "admin-taxonomia-contract@runcriticon.local"
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
