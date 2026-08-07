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
 * Contrato REST runtime contra `api/openapi.yaml` para el alta de grupos y la previsualización de miembros. Mismo
 * patrón que `TaxonomiaOpenApiContractTest`: backend arrancado con Testcontainers, login real por HTTP, sin mocks.
 *
 * Es el primer endpoint de la API con un query param repetible, así que aquí es donde queda ejercitado que el
 * binding de `?tagValueId=a&tagValueId=b` casa con lo declarado en la spec, y que la ausencia del parámetro no es un
 * error sino un filtro vacío.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class GruposOpenApiContractTest {
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
                name = "Admin Grupos Contract",
                role = "ADMIN",
                passwordHash = encoder.encode(PASSWORD),
                status = "ACTIVO",
                createdAt = ahora,
                modifiedAt = ahora,
            ),
        )
    }

    @Test
    fun `crear un grupo y previsualizar sus miembros cumple el contrato OpenAPI`() {
        autenticar()
        val valorId = crearValor("Nivel grupos contrato", "Medio")

        verificar(HttpMethod.POST, "/api/grupos", "/grupos", HttpStatus.CREATED) {
            postJson(it, """{"nombre":"Grupo contrato","valores":["$valorId"]}""")
        }
        verificar(
            HttpMethod.GET,
            "/api/grupos/miembros?tagValueId=$valorId",
            "/grupos/miembros",
            HttpStatus.OK,
        )
    }

    /** Sin filtro no hay error: la respuesta es un conjunto vacío, que el constructor pinta como "0 alumnos". */
    @Test
    fun `previsualizar sin filtro devuelve cero alumnos y cumple el contrato`() {
        autenticar()

        val respuesta =
            verificar(HttpMethod.GET, "/api/grupos/miembros", "/grupos/miembros", HttpStatus.OK)

        assertEquals(0, json.readTree(respuesta.body).get("total").asInt())
    }

    @Test
    fun `el 404 de un valor inexistente al crear el grupo cumple el contrato`() {
        autenticar()

        val respuesta = postJson("/api/grupos", """{"nombre":"Grupo fantasma","valores":["${UUID.randomUUID()}"]}""")

        assertEquals(HttpStatus.NOT_FOUND, respuesta.statusCode, respuesta.body.orEmpty())
        assertEquals("TAG_VALUE_NOT_FOUND", json.readTree(respuesta.body).get("code").asText())
        assertContract(Request.Method.POST, "/grupos", HttpStatus.NOT_FOUND, respuesta.body)
    }

    @Test
    fun `el 409 de un valor archivado al previsualizar cumple el contrato`() {
        autenticar()
        val valorId = crearValor("Terreno grupos contrato", "Montaña")
        val archivado = putVacio("/api/taxonomia/valores/archivados/$valorId")
        assertEquals(HttpStatus.OK, archivado.statusCode, archivado.body.orEmpty())

        val respuesta = get("/api/grupos/miembros?tagValueId=$valorId")

        assertEquals(HttpStatus.CONFLICT, respuesta.statusCode, respuesta.body.orEmpty())
        assertEquals("TAG_VALUE_NOT_ASSIGNABLE", json.readTree(respuesta.body).get("code").asText())
        assertContract(Request.Method.GET, "/grupos/miembros", HttpStatus.CONFLICT, respuesta.body)
    }

    private fun crearValor(
        eje: String,
        valor: String,
    ): String {
        val tagId = idDe(postJson("/api/taxonomia/tags", """{"nombre":"$eje"}"""))
        return idDe(postJson("/api/taxonomia/tags/$tagId/valores", """{"valor":"$valor"}"""))
    }

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
            else -> error("Método no usado por este contrato: $metodo")
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

    private fun putVacio(ruta: String): ResponseEntity<String> = intercambiar(ruta, HttpMethod.PUT, null)

    private fun postJson(
        ruta: String,
        cuerpo: String?,
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
        private const val EMAIL = "admin-grupos-contract@runcriticon.local"
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
