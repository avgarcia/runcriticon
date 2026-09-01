package com.runcriticon.seguimiento.contracts

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
 * Contrato REST runtime contra `api/openapi.yaml` para `/me/marcas*` (LAL-31). Mismo patrón que
 * `MiReportesOpenApiContractTest`: backend arrancado con Testcontainers, login real por HTTP, sin mocks.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class MisMarcasOpenApiContractTest {
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
    fun sembrarUsuarios() {
        sembrar(alumnoId, ALUMNO_EMAIL, "ALUMNO")
        sembrar(entrenadorId, ENTRENADOR_EMAIL, "ENTRENADOR")
        // Cada test parte de cero: sin esto, la marca de un test anterior contaminaría el siguiente.
        jdbc.update("DELETE FROM seguimiento.marca_alumno WHERE alumno_id = ?", alumnoId)
    }

    private fun sembrar(
        userId: UUID,
        email: String,
        role: String,
    ) {
        if (usuarios.findByClubIdAndNormalizedEmail(clubId, email) != null) return
        val ahora = Instant.now()
        usuarios.save(
            UserEntity(
                id = userId,
                clubId = clubId,
                email = email,
                normalizedEmail = email,
                name = "Usuario contrato $role",
                role = role,
                passwordHash = encoder.encode(PASSWORD),
                status = "ACTIVO",
                createdAt = ahora,
                modifiedAt = ahora,
            ),
        )
    }

    @Test
    fun `consultar marcas sin ninguna registrada cumple el contrato con las cuatro distancias sin valor`() {
        autenticar(ALUMNO_EMAIL)

        val respuesta = verificarGet("/api/me/marcas", "/me/marcas", HttpStatus.OK)

        val marcas = json.readTree(respuesta.body).get("marcas")
        assertEquals(4, marcas.size())
        marcas.forEach { assertTrue(it.get("tiempoSegundos") == null || it.get("tiempoSegundos").isNull) }
    }

    @Test
    fun `registrar una marca de 10K cumple el contrato y queda persistida`() {
        autenticar(ALUMNO_EMAIL)

        val respuesta =
            verificarPut("/api/me/marcas/10K", "/me/marcas/{distancia}", """{"tiempoSegundos":2850}""", HttpStatus.OK)

        val cuerpo = json.readTree(respuesta.body)
        assertEquals("10K", cuerpo.get("distancia").asText())
        assertEquals(2850, cuerpo.get("tiempoSegundos").asInt())
    }

    @Test
    fun `registrar dos veces la misma distancia edita, no duplica, y cumple el contrato`() {
        autenticar(ALUMNO_EMAIL)
        verificarPut("/api/me/marcas/5K", "/me/marcas/{distancia}", """{"tiempoSegundos":1400}""", HttpStatus.OK)

        val respuesta =
            verificarPut("/api/me/marcas/5K", "/me/marcas/{distancia}", """{"tiempoSegundos":1365}""", HttpStatus.OK)

        assertEquals(1365, json.readTree(respuesta.body).get("tiempoSegundos").asInt())
        val filas =
            jdbc.queryForObject(
                "SELECT count(*) FROM seguimiento.marca_alumno WHERE alumno_id = ?",
                Int::class.java,
                alumnoId,
            )
        assertEquals(1, filas)
    }

    @Test
    fun `un tiempo cero da 400 TIEMPO_INVALIDO y cumple el contrato`() {
        autenticar(ALUMNO_EMAIL)

        val respuesta =
            verificarPut(
                "/api/me/marcas/5K",
                "/me/marcas/{distancia}",
                """{"tiempoSegundos":0}""",
                HttpStatus.BAD_REQUEST,
            )

        assertEquals("TIEMPO_INVALIDO", json.readTree(respuesta.body).get("code").asText())
    }

    @Test
    fun `retirar una marca existente da 204 y cumple el contrato`() {
        autenticar(ALUMNO_EMAIL)
        verificarPut("/api/me/marcas/21K", "/me/marcas/{distancia}", """{"tiempoSegundos":6300}""", HttpStatus.OK)

        verificarDelete("/api/me/marcas/21K", "/me/marcas/{distancia}", HttpStatus.NO_CONTENT)

        val filas =
            jdbc.queryForObject(
                "SELECT count(*) FROM seguimiento.marca_alumno WHERE alumno_id = ? AND distancia = '21K'",
                Int::class.java,
                alumnoId,
            )
        assertEquals(0, filas)
    }

    @Test
    fun `retirar una marca inexistente es idempotente y tambien da 204`() {
        autenticar(ALUMNO_EMAIL)

        verificarDelete("/api/me/marcas/42K", "/me/marcas/{distancia}", HttpStatus.NO_CONTENT)
    }

    @Test
    fun `un entrenador no puede consultar marcas, y el codigo es FORBIDDEN`() {
        autenticar(ENTRENADOR_EMAIL)

        val respuesta = verificarGet("/api/me/marcas", "/me/marcas", HttpStatus.FORBIDDEN)

        assertEquals("FORBIDDEN", json.readTree(respuesta.body).get("code").asText())
    }

    @Test
    fun `un entrenador no puede registrar una marca, y el codigo es FORBIDDEN`() {
        autenticar(ENTRENADOR_EMAIL)

        val respuesta =
            verificarPut(
                "/api/me/marcas/10K",
                "/me/marcas/{distancia}",
                """{"tiempoSegundos":2850}""",
                HttpStatus.FORBIDDEN,
            )

        assertEquals("FORBIDDEN", json.readTree(respuesta.body).get("code").asText())
    }

    private fun verificarGet(
        ruta: String,
        specPath: String,
        esperado: HttpStatus,
    ): ResponseEntity<String> {
        val respuesta = get(ruta)
        assertEquals(esperado, respuesta.statusCode, respuesta.body.orEmpty())
        assertContract(specPath, Request.Method.GET, esperado, respuesta.body)
        return respuesta
    }

    private fun verificarPut(
        ruta: String,
        specPath: String,
        cuerpoRequest: String,
        esperado: HttpStatus,
    ): ResponseEntity<String> {
        val respuesta = putJson(ruta, cuerpoRequest)
        assertEquals(esperado, respuesta.statusCode, respuesta.body.orEmpty())
        assertContract(specPath, Request.Method.PUT, esperado, respuesta.body)
        return respuesta
    }

    private fun verificarDelete(
        ruta: String,
        specPath: String,
        esperado: HttpStatus,
    ): ResponseEntity<String> {
        val respuesta = delete(ruta)
        assertEquals(esperado, respuesta.statusCode, respuesta.body.orEmpty())
        assertContract(specPath, Request.Method.DELETE, esperado, respuesta.body)
        return respuesta
    }

    private fun autenticar(email: String) {
        get("/api/sesion/actual") // handshake CSRF
        val login = postJson("/api/sesion", """{"email":"$email","password":"$PASSWORD"}""")
        assertEquals(HttpStatus.OK, login.statusCode, login.body.orEmpty())
    }

    private fun assertContract(
        specPath: String,
        method: Request.Method,
        status: HttpStatus,
        body: String?,
    ) {
        val builder = SimpleResponse.Builder(status.value())
        if (!body.isNullOrEmpty()) {
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

    private fun postJson(
        ruta: String,
        cuerpo: String?,
    ): ResponseEntity<String> = intercambiar(ruta, HttpMethod.POST, cuerpo)

    private fun putJson(
        ruta: String,
        cuerpo: String?,
    ): ResponseEntity<String> = intercambiar(ruta, HttpMethod.PUT, cuerpo)

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
        // Único club sembrado por V202607210001__crea_club.sql (MVP mono-club, ADR-0006): no se puede inventar
        // uno nuevo, `identidad.usuario.club_id` tiene FK contra `identidad.club`.
        private val clubId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        private val alumnoId: UUID = UuidCreator.getTimeOrderedEpoch()
        private val entrenadorId: UUID = UuidCreator.getTimeOrderedEpoch()
        private const val ALUMNO_EMAIL = "alumno-marcas-contract@runcriticon.local"
        private const val ENTRENADOR_EMAIL = "entrenador-marcas-contract@runcriticon.local"
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
