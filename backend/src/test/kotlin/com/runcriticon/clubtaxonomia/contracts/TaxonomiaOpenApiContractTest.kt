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
    fun `el ciclo de vida de un eje cumple el contrato OpenAPI`() {
        autenticar()
        val tagId = crearTag("Nivel contrato")

        verificar(HttpMethod.PATCH, "/api/taxonomia/tags/$tagId", "/taxonomia/tags/{tagId}", HttpStatus.OK) {
            patchJson(it, """{"nombre":"Nivel contrato editado"}""")
        }
        verificar(
            HttpMethod.POST,
            "/api/taxonomia/tags/$tagId/archivado",
            "/taxonomia/tags/{tagId}/archivado",
            HttpStatus.OK,
        )
        verificar(
            HttpMethod.DELETE,
            "/api/taxonomia/tags/$tagId/archivado",
            "/taxonomia/tags/{tagId}/archivado",
            HttpStatus.OK,
        )
        verificar(HttpMethod.GET, "/api/taxonomia", "/taxonomia", HttpStatus.OK)
    }

    @Test
    fun `el ciclo de vida de un valor cumple el contrato OpenAPI`() {
        autenticar()
        val tagId = crearTag("Distancia contrato")

        val creado =
            verificar(
                HttpMethod.POST,
                "/api/taxonomia/tags/$tagId/valores",
                "/taxonomia/tags/{tagId}/valores",
                HttpStatus.CREATED,
            ) { postJson(it, """{"valor":"Principiante"}""") }
        val valorId = idDe(creado)

        verificar(HttpMethod.PATCH, "/api/taxonomia/valores/$valorId", "/taxonomia/valores/{valorId}", HttpStatus.OK) {
            patchJson(it, """{"valor":"Iniciación"}""")
        }
        verificar(
            HttpMethod.POST,
            "/api/taxonomia/valores/$valorId/archivado",
            "/taxonomia/valores/{valorId}/archivado",
            HttpStatus.OK,
        )
        verificar(
            HttpMethod.DELETE,
            "/api/taxonomia/valores/$valorId/archivado",
            "/taxonomia/valores/{valorId}/archivado",
            HttpStatus.OK,
        )
    }

    private fun crearTag(nombre: String): String =
        idDe(
            verificar(HttpMethod.POST, "/api/taxonomia/tags", "/taxonomia/tags", HttpStatus.CREATED) {
                postJson(it, """{"nombre":"$nombre"}""")
            },
        )

    /** Ejecuta la llamada, comprueba el status y valida el cuerpo contra la spec. */
    private fun verificar(
        metodo: HttpMethod,
        ruta: String,
        specPath: String,
        esperado: HttpStatus,
        llamada: (String) -> ResponseEntity<String> = { intercambiar(it, metodo, null) },
    ): ResponseEntity<String> {
        val respuesta = llamada(ruta)
        assertEquals(esperado, respuesta.statusCode, respuesta.body.orEmpty())
        assertContract(metodoSpec(metodo), specPath, esperado, respuesta.body)
        return respuesta
    }

    private fun metodoSpec(metodo: HttpMethod): Request.Method =
        when (metodo) {
            HttpMethod.GET -> Request.Method.GET
            HttpMethod.POST -> Request.Method.POST
            HttpMethod.PATCH -> Request.Method.PATCH
            HttpMethod.DELETE -> Request.Method.DELETE
            else -> error("Método no usado por este contrato: $metodo")
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
