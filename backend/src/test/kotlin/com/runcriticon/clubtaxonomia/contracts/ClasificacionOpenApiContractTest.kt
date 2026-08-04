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
 * Contrato REST runtime de la clasificación de alumnos contra `api/openapi.yaml`, con el backend arrancado y login
 * real por HTTP.
 *
 * Fija además que `/api/alumnos/{id}/tags` **no colisiona** con las rutas de alta de alumnos, que las sirve otro
 * módulo bajo el mismo prefijo: es la primera vez en el repo que dos controladores comparten prefijo de URL, y esa
 * convivencia no estaba ejercitada en ningún sitio.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ClasificacionOpenApiContractTest {
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
    fun sembrarAdmin() {
        if (usuarios.findByClubIdAndNormalizedEmail(clubId, EMAIL) != null) return
        val ahora = Instant.now()
        usuarios.save(
            UserEntity(
                id = UuidCreator.getTimeOrderedEpoch(),
                clubId = clubId,
                email = EMAIL,
                normalizedEmail = EMAIL,
                name = "Admin Clasificacion Contract",
                role = "ADMIN",
                passwordHash = encoder.encode(PASSWORD),
                status = "ACTIVO",
                createdAt = ahora,
                modifiedAt = ahora,
            ),
        )
    }

    @Test
    fun `el ciclo completo de clasificacion cumple el contrato OpenAPI`() {
        autenticar()
        val alumno = sembrarAlumno()
        val valores = sembrarValores()

        verificar(HttpMethod.GET, "/api/alumnos/$alumno/tags", "/alumnos/{id}/tags", HttpStatus.OK)
        verificar(HttpMethod.PUT, "/api/alumnos/$alumno/tags", "/alumnos/{id}/tags", HttpStatus.OK) {
            intercambiar(it, HttpMethod.PUT, """{"valores":["${valores.first}"]}""")
        }
        verificar(HttpMethod.POST, "/api/alumnos/$alumno/tags", "/alumnos/{id}/tags", HttpStatus.OK) {
            intercambiar(it, HttpMethod.POST, """{"valorId":"${valores.second}"}""")
        }
        verificar(
            HttpMethod.DELETE,
            "/api/alumnos/$alumno/tags/${valores.first}",
            "/alumnos/{id}/tags/{valorId}",
            HttpStatus.NO_CONTENT,
        )

        val restantes = json.readTree(get("/api/alumnos/$alumno/tags").body).get("valores")
        assertEquals(1, restantes.size())
        assertEquals(valores.second, restantes.get(0).get("id").asText())
    }

    @Test
    fun `el 404 de un alumno inexistente cumple el contrato`() {
        autenticar()

        val respuesta = get("/api/alumnos/${UUID.randomUUID()}/tags")

        assertEquals(HttpStatus.NOT_FOUND, respuesta.statusCode, respuesta.body.orEmpty())
        assertEquals("STUDENT_NOT_FOUND", json.readTree(respuesta.body).get("code").asText())
        assertContract(Request.Method.GET, "/alumnos/{id}/tags", HttpStatus.NOT_FOUND, respuesta.body)
    }

    @Test
    fun `el 409 de un valor archivado cumple el contrato`() {
        autenticar()
        val alumno = sembrarAlumno()
        val valores = sembrarValores()
        jdbc.update(
            "UPDATE club_taxonomia.tag_value SET archivado_en = now() WHERE id = ?",
            UUID.fromString(valores.first),
        )

        val respuesta = intercambiar("/api/alumnos/$alumno/tags", HttpMethod.POST, """{"valorId":"${valores.first}"}""")

        assertEquals(HttpStatus.CONFLICT, respuesta.statusCode, respuesta.body.orEmpty())
        assertEquals("TAG_VALUE_NOT_ASSIGNABLE", json.readTree(respuesta.body).get("code").asText())
        assertContract(Request.Method.POST, "/alumnos/{id}/tags", HttpStatus.CONFLICT, respuesta.body)
    }

    /** El alta de alumnos la sirve otro módulo bajo el mismo prefijo; ninguna de las dos rutas debe tapar a la otra. */
    @Test
    fun `la ruta de clasificacion convive con la de alta de alumnos`() {
        autenticar()

        val alta = intercambiar("/api/alumnos", HttpMethod.POST, """{"nombre":"Nuevo","email":"nuevo@club.test"}""")

        assertTrue(
            alta.statusCode == HttpStatus.CREATED || alta.statusCode == HttpStatus.CONFLICT,
            "El alta de alumnos dejó de responder: ${alta.statusCode} ${alta.body.orEmpty()}",
        )
    }

    private fun sembrarAlumno(): UUID {
        val id = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            """
            INSERT INTO club_taxonomia.persona
                (id, club_id, nombre, email, rol, estado, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, 'ALUMNO', 'ACTIVO', ?, now())
            """.trimIndent(),
            id,
            clubId,
            "Alumno Contrato",
            "alumno-$id@club.test",
            UuidCreator.getTimeOrderedEpoch(),
        )
        return id
    }

    /** Un eje propio con dos valores; el sufijo sale del final del UUID porque los v7 comparten prefijo temporal. */
    private fun sembrarValores(): Pair<String, String> {
        val keyId = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            "INSERT INTO club_taxonomia.tag_key (id, club_id, nombre) VALUES (?, ?, ?)",
            keyId,
            clubId,
            "contrato-${keyId.toString().takeLast(SUFIJO_UNICO)}",
        )
        val primero = UuidCreator.getTimeOrderedEpoch()
        val segundo = UuidCreator.getTimeOrderedEpoch()
        listOf(primero to "uno", segundo to "dos").forEach { (id, nombre) ->
            jdbc.update(
                "INSERT INTO club_taxonomia.tag_value (id, tag_key_id, club_id, nombre) VALUES (?, ?, ?, ?)",
                id,
                keyId,
                clubId,
                nombre,
            )
        }
        return primero.toString() to segundo.toString()
    }

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
            HttpMethod.PUT -> Request.Method.PUT
            HttpMethod.DELETE -> Request.Method.DELETE
            else -> error("Método no usado por este contrato: $metodo")
        }

    private fun autenticar() {
        get("/api/sesion/actual") // handshake CSRF
        val login = intercambiar("/api/sesion", HttpMethod.POST, """{"email":"$EMAIL","password":"$PASSWORD"}""")
        assertEquals(HttpStatus.OK, login.statusCode, login.body.orEmpty())
    }

    private fun assertContract(
        method: Request.Method,
        specPath: String,
        status: HttpStatus,
        body: String?,
    ) {
        val builder = SimpleResponse.Builder(status.value())
        if (!body.isNullOrBlank()) {
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
        private const val EMAIL = "admin-clasificacion-contract@runcriticon.local"
        private const val PASSWORD = "contract-test-password-12345"
        private const val SUFIJO_UNICO = 8

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
