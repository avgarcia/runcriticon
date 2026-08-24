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
import java.sql.Timestamp
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Contrato REST runtime contra `api/openapi.yaml` para `GET /me/plan` (LAL-29). Mismo patrón que
 * `PlanesOpenApiContractTest`: backend arrancado con Testcontainers, login real por HTTP, sin mocks.
 *
 * Las filas de `plan_resuelto_por_alumno` se siembran con SQL directo, no vía `PlanPublicado`: probar el
 * contrato REST no necesita reproducir el flujo de eventos completo — eso ya lo cubre
 * `ResolvedPlanProjectionEventFlowIntegrationTest`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class MiPlanOpenApiContractTest {
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
        // Cada test parte de cero: las filas se siembran con SQL directo en el propio test, y sin limpiar aquí
        // las de un test anterior contaminarían "una semana sin filas resuelve sesiones vacío".
        jdbc.update("DELETE FROM seguimiento.plan_resuelto_por_alumno WHERE alumno_id = ?", alumnoId)
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

    /** Fila sembrada con SQL directo: aísla el contrato REST del flujo de eventos. */
    private fun sembrarFilaResuelta(
        dia: LocalDate,
        payload: String = """{"tipo":"RODAJE","volumenTipo":"DISTANCIA","volumenMetros":8000,"notas":"suave"}""",
    ) {
        jdbc.update(
            """
            INSERT INTO seguimiento.plan_resuelto_por_alumno
                (alumno_id, plan_id, club_id, dia, sesion_resuelta, mensaje_al_alumno, es_personalizada,
                 last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, ?::jsonb, NULL, FALSE, ?, ?)
            """.trimIndent(),
            alumnoId,
            UUID.randomUUID(),
            clubId,
            dia,
            payload,
            UUID.randomUUID(),
            Timestamp.from(Instant.now()),
        )
    }

    @Test
    fun `la semana resuelta del alumno cumple el contrato y refleja lo sembrado`() {
        autenticar(ALUMNO_EMAIL)
        sembrarFilaResuelta(LocalDate.parse("2026-08-17"))
        sembrarFilaResuelta(LocalDate.parse("2026-08-19"), """{"tipo":"DESCANSO"}""")

        val respuesta = verificar(HttpMethod.GET, "/api/me/plan?semana=2026-08-17", "/me/plan", HttpStatus.OK)

        val cuerpo = json.readTree(respuesta.body)
        assertEquals("2026-08-17", cuerpo.get("semana").asText())
        assertEquals(2, cuerpo.get("sesiones").size())
        val rodaje = cuerpo.get("sesiones").first { it.get("dia").asText() == "2026-08-17" }
        assertEquals("RODAJE", rodaje.get("tipo").asText())
        assertEquals("DISTANCIA", rodaje.get("volumen").get("tipo").asText())
        assertEquals(8000, rodaje.get("volumen").get("metros").asInt())
        assertEquals("suave", rodaje.get("notas").asText())
    }

    @Test
    fun `una semana sin filas resuelve sesiones vacio y cumple el contrato`() {
        autenticar(ALUMNO_EMAIL)

        val respuesta = verificar(HttpMethod.GET, "/api/me/plan?semana=2026-08-17", "/me/plan", HttpStatus.OK)

        assertTrue(json.readTree(respuesta.body).get("sesiones").isEmpty)
    }

    @Test
    fun `sin parametro semana usa el lunes en curso y cumple el contrato`() {
        autenticar(ALUMNO_EMAIL)

        val respuesta = verificar(HttpMethod.GET, "/api/me/plan", "/me/plan", HttpStatus.OK)

        val semana = LocalDate.parse(json.readTree(respuesta.body).get("semana").asText())
        assertEquals(DayOfWeek.MONDAY, semana.dayOfWeek)
    }

    @Test
    fun `una semana que no es lunes da 400 y cumple el contrato`() {
        autenticar(ALUMNO_EMAIL)

        val respuesta = verificar(HttpMethod.GET, "/api/me/plan?semana=2026-08-18", "/me/plan", HttpStatus.BAD_REQUEST)

        assertEquals("WEEK_NOT_MONDAY", json.readTree(respuesta.body).get("code").asText())
    }

    @Test
    fun `un entrenador no puede ver la semana resuelta de un alumno`() {
        autenticar(ENTRENADOR_EMAIL)

        val respuesta = verificar(HttpMethod.GET, "/api/me/plan?semana=2026-08-17", "/me/plan", HttpStatus.FORBIDDEN)

        assertEquals("FORBIDDEN", json.readTree(respuesta.body).get("code").asText())
    }

    @Test
    fun `la personalizacion no se expone en el contrato, solo el mensaje del entrenador`() {
        autenticar(ALUMNO_EMAIL)
        sembrarFilaResuelta(LocalDate.parse("2026-08-17"))

        val respuesta = verificar(HttpMethod.GET, "/api/me/plan?semana=2026-08-17", "/me/plan", HttpStatus.OK)

        val sesion = json.readTree(respuesta.body).get("sesiones").first()
        assertTrue(sesion.get("esPersonalizada") == null, "esPersonalizada no debe aparecer en el contrato")
    }

    /** Ejecuta la llamada, comprueba el status y valida el cuerpo contra la spec. */
    private fun verificar(
        metodo: HttpMethod,
        ruta: String,
        specPath: String,
        esperado: HttpStatus,
    ): ResponseEntity<String> {
        val respuesta = intercambiar(ruta, metodo, null)
        assertEquals(esperado, respuesta.statusCode, respuesta.body.orEmpty())
        assertContract(metodoSpec(metodo), specPath, esperado, respuesta.body)
        return respuesta
    }

    private fun metodoSpec(metodo: HttpMethod): Request.Method =
        when (metodo) {
            HttpMethod.GET -> Request.Method.GET
            else -> error("Método no usado por este contrato: $metodo")
        }

    private fun autenticar(email: String) {
        get("/api/sesion/actual") // handshake CSRF
        val login = postJson("/api/sesion", """{"email":"$email","password":"$PASSWORD"}""")
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
        // Único club sembrado por V202607210001__crea_club.sql (MVP mono-club, ADR-0006): no se puede inventar
        // uno nuevo, `identidad.usuario.club_id` tiene FK contra `identidad.club`.
        private val clubId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        private val alumnoId: UUID = UuidCreator.getTimeOrderedEpoch()
        private val entrenadorId: UUID = UuidCreator.getTimeOrderedEpoch()
        private const val ALUMNO_EMAIL = "alumno-miplan-contract@runcriticon.local"
        private const val ENTRENADOR_EMAIL = "entrenador-miplan-contract@runcriticon.local"
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
