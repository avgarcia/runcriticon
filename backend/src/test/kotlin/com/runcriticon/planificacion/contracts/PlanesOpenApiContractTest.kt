package com.runcriticon.planificacion.contracts

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
 * Contrato REST runtime contra `api/openapi.yaml` para el alta y el listado de planes en borrador. Mismo patrón
 * que `GruposOpenApiContractTest`: backend arrancado con Testcontainers, login real por HTTP, sin mocks.
 *
 * El principal es ENTRENADOR, no ADMIN: `PLAN:CREATE`/`PLAN:LIST` son solo suyos (ver `AuthorizationMatrix`). La
 * relación entrenador↔grupo se siembra directamente en `planificacion.miembro_grupo` -- es la proyección que
 * `CoachGroupLookup` lee, y poblarla vía el flujo real de eventos (LAL-93 + LAL-94) convertiría este test en una
 * espera asíncrona en vez de una comprobación de contrato.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PlanesOpenApiContractTest {
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
    fun sembrarEntrenador() {
        if (usuarios.findByClubIdAndNormalizedEmail(clubId, EMAIL) != null) return
        val ahora = Instant.now()
        usuarios.save(
            UserEntity(
                id = entrenadorId,
                clubId = clubId,
                email = EMAIL,
                normalizedEmail = EMAIL,
                name = "Entrenador Planes Contract",
                role = "ENTRENADOR",
                passwordHash = encoder.encode(PASSWORD),
                status = "ACTIVO",
                createdAt = ahora,
                modifiedAt = ahora,
            ),
        )
    }

    /** Siembra la relación entrenador↔grupo que lee `CoachGroupLookup`, sin pasar por el flujo de eventos. */
    private fun sembrarMiembroGrupo(groupId: UUID) {
        jdbc.update(
            """
            INSERT INTO planificacion.miembro_grupo
                (grupo_id, club_id, persona_id, rol, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, 'ENTRENADOR', ?, now())
            """.trimIndent(),
            groupId,
            clubId,
            entrenadorId,
            UuidCreator.getTimeOrderedEpoch(),
        )
    }

    /** Siembra un alumno del grupo, para que `publicarPlan` (LAL-25) tenga a alguien en el snapshot. */
    private fun sembrarAlumnoDeGrupo(groupId: UUID) {
        jdbc.update(
            """
            INSERT INTO planificacion.miembro_grupo
                (grupo_id, club_id, persona_id, rol, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, 'ALUMNO', ?, now())
            """.trimIndent(),
            groupId,
            clubId,
            UUID.randomUUID(),
            UuidCreator.getTimeOrderedEpoch(),
        )
    }

    @Test
    fun `crear un plan en borrador cumple el contrato OpenAPI`() {
        autenticar()
        val groupId = UUID.randomUUID()
        sembrarMiembroGrupo(groupId)

        val respuesta =
            verificar(HttpMethod.POST, "/api/planes", "/planes", HttpStatus.CREATED) {
                postJson(it, """{"grupoId":"$groupId","semana":"2026-08-17"}""")
            }

        assertEquals(groupId.toString(), json.readTree(respuesta.body).get("grupoId").asText())
        assertEquals("BORRADOR", json.readTree(respuesta.body).get("estado").asText())
    }

    @Test
    fun `el listado de planes en borrador cumple el contrato y refleja lo creado`() {
        autenticar()
        val groupId = UUID.randomUUID()
        sembrarMiembroGrupo(groupId)
        postJson("/api/planes", """{"grupoId":"$groupId","semana":"2026-08-17"}""")

        val respuesta =
            verificar(HttpMethod.GET, "/api/planes?grupoId=$groupId", "/planes", HttpStatus.OK)

        assertEquals(1, json.readTree(respuesta.body).get("planes").size())
    }

    @Test
    fun `un entrenador sin relacion con el grupo ve lista vacia, no un error, y cumple el contrato`() {
        autenticar()
        val ajeno = UUID.randomUUID()

        val respuesta = verificar(HttpMethod.GET, "/api/planes?grupoId=$ajeno", "/planes", HttpStatus.OK)

        assertTrue(json.readTree(respuesta.body).get("planes").isEmpty)
    }

    @Test
    fun `una semana que no es lunes da 400 y cumple el contrato`() {
        autenticar()
        val groupId = UUID.randomUUID()
        sembrarMiembroGrupo(groupId)

        val respuesta =
            verificar(HttpMethod.POST, "/api/planes", "/planes", HttpStatus.BAD_REQUEST) {
                postJson(it, """{"grupoId":"$groupId","semana":"2026-08-18"}""")
            }

        assertEquals("WEEK_NOT_MONDAY", json.readTree(respuesta.body).get("code").asText())
    }

    @Test
    fun `consultar el detalle de un plan cumple el contrato`() {
        autenticar()
        val planId = crearPlan()

        val respuesta = verificar(HttpMethod.GET, "/api/planes/$planId", "/planes/{planId}", HttpStatus.OK)

        assertEquals(planId, json.readTree(respuesta.body).get("id").asText())
        assertTrue(json.readTree(respuesta.body).get("sesiones").isEmpty)
    }

    @Test
    fun `anadir una sesion cumple el contrato`() {
        autenticar()
        val planId = crearPlan()

        val respuesta =
            verificar(
                HttpMethod.POST,
                "/api/planes/$planId/sesiones",
                "/planes/{planId}/sesiones",
                HttpStatus.CREATED,
            ) {
                postJson(it, SESSION_ABSOLUTO_BODY)
            }

        assertEquals("SERIES", json.readTree(respuesta.body).get("tipo").asText())
        assertEquals(
            "DISTANCIA",
            json
                .readTree(respuesta.body)
                .get("volumen")
                .get("tipo")
                .asText(),
        )
    }

    @Test
    fun `anadir dos sesiones el mismo dia da 409 y cumple el contrato`() {
        autenticar()
        val planId = crearPlan()
        postJson("/api/planes/$planId/sesiones", SESSION_ABSOLUTO_BODY)

        val respuesta =
            verificar(
                HttpMethod.POST,
                "/api/planes/$planId/sesiones",
                "/planes/{planId}/sesiones",
                HttpStatus.CONFLICT,
            ) {
                postJson(it, SESSION_ABSOLUTO_BODY)
            }

        assertEquals("DUPLICATE_SESSION_DAY", json.readTree(respuesta.body).get("code").asText())
    }

    @Test
    fun `editar una sesion cumple el contrato y no cambia el dia`() {
        autenticar()
        val planId = crearPlan()
        val sesionId =
            json
                .readTree(
                    postJson("/api/planes/$planId/sesiones", SESSION_ABSOLUTO_BODY).body,
                ).get("id")
                .asText()

        val respuesta =
            verificar(
                HttpMethod.PUT,
                "/api/planes/$planId/sesiones/$sesionId",
                "/planes/{planId}/sesiones/{sesionId}",
                HttpStatus.OK,
            ) { intercambiar(it, HttpMethod.PUT, """{"tipo":"RODAJE","notas":"editada"}""") }

        assertEquals("RODAJE", json.readTree(respuesta.body).get("tipo").asText())
        assertEquals("2026-08-19", json.readTree(respuesta.body).get("dia").asText())
    }

    @Test
    fun `eliminar una sesion cumple el contrato`() {
        autenticar()
        val planId = crearPlan()
        val sesionId =
            json
                .readTree(
                    postJson("/api/planes/$planId/sesiones", SESSION_ABSOLUTO_BODY).body,
                ).get("id")
                .asText()

        verificar(
            HttpMethod.DELETE,
            "/api/planes/$planId/sesiones/$sesionId",
            "/planes/{planId}/sesiones/{sesionId}",
            HttpStatus.NO_CONTENT,
        )
    }

    @Test
    fun `publicar un plan con sesiones cumple el contrato y congela el snapshot`() {
        autenticar()
        val groupId = UUID.randomUUID()
        sembrarMiembroGrupo(groupId)
        sembrarAlumnoDeGrupo(groupId)
        val planId =
            json
                .readTree(
                    postJson("/api/planes", """{"grupoId":"$groupId","semana":"2026-08-17"}""").body,
                ).get("id")
                .asText()
        postJson("/api/planes/$planId/sesiones", SESSION_ABSOLUTO_BODY)

        val respuesta =
            verificar(
                HttpMethod.POST,
                "/api/planes/$planId/publicacion",
                "/planes/{planId}/publicacion",
                HttpStatus.OK,
            )

        assertEquals(
            "PUBLICADO",
            json
                .readTree(respuesta.body)
                .get("plan")
                .get("estado")
                .asText(),
        )
        assertEquals(1, json.readTree(respuesta.body).get("alumnosEnSnapshot").asInt())
    }

    @Test
    fun `publicar un plan sin sesiones da 409 y cumple el contrato`() {
        autenticar()
        val planId = crearPlan()

        val respuesta =
            verificar(
                HttpMethod.POST,
                "/api/planes/$planId/publicacion",
                "/planes/{planId}/publicacion",
                HttpStatus.CONFLICT,
            )

        assertEquals("PLAN_WITHOUT_SESSIONS", json.readTree(respuesta.body).get("code").asText())
    }

    /** Crea un plan en borrador para un grupo nuevo y devuelve su id. */
    private fun crearPlan(): String {
        val groupId = UUID.randomUUID()
        sembrarMiembroGrupo(groupId)
        val respuesta = postJson("/api/planes", """{"grupoId":"$groupId","semana":"2026-08-17"}""")
        return json.readTree(respuesta.body).get("id").asText()
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
            HttpMethod.PUT -> Request.Method.PUT
            HttpMethod.DELETE -> Request.Method.DELETE
            else -> error("Método no usado por este contrato: $metodo")
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
        private val entrenadorId: UUID = UuidCreator.getTimeOrderedEpoch()
        private const val EMAIL = "entrenador-planes-contract@runcriticon.local"
        private const val PASSWORD = "contract-test-password-12345"

        // "2026-08-19" cae dentro de la semana del plan sembrado por `crearPlan()` (lunes 2026-08-17).
        private const val SESSION_ABSOLUTO_BODY =
            """{"dia":"2026-08-19","tipo":"SERIES","volumen":{"tipo":"DISTANCIA","metros":4000},""" +
                """"ritmo":{"tipo":"ABSOLUTO","segundosPorKm":225},"notas":"8x400m"}"""

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
