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
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Contrato REST runtime contra `api/openapi.yaml` para `PUT /me/reportes/{dia}` (LAL-30). Mismo patrón que
 * `MiPlanOpenApiContractTest`: backend arrancado con Testcontainers, login real por HTTP, sin mocks.
 *
 * Cada test siembra su propia fila de `plan_resuelto_por_alumno` con SQL directo — probar el contrato REST
 * no necesita reproducir `PlanPublicado`, eso ya lo cubre `ResolvedPlanProjectionEventFlowIntegrationTest`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class MiReportesOpenApiContractTest {
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
        // Cada test parte de cero: sin esto, filas de un test anterior contaminarían el desempate multi-plan.
        jdbc.update("DELETE FROM seguimiento.reporte_sesion WHERE alumno_id = ?", alumnoId)
        jdbc.update("DELETE FROM seguimiento.plan_resuelto_por_alumno WHERE alumno_id = ?", alumnoId)
        // Consentimiento vigente por defecto (LAL-128 PR2, gate fail-closed): sin esta fila, todos los envíos
        // de este contrato darían 403 CONSENTIMIENTO_NO_VIGENTE. El propio rechazo tiene su test dedicado,
        // que la borra explícitamente.
        jdbc.update("DELETE FROM seguimiento.consentimiento_alumno WHERE alumno_id = ?", alumnoId)
        sembrarConsentimientoVigente()
    }

    private fun sembrarConsentimientoVigente() {
        jdbc.update(
            """
            INSERT INTO seguimiento.consentimiento_alumno
                (alumno_id, club_id, vigente, version_texto, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, TRUE, 'v2026-08-25', ?, now())
            """.trimIndent(),
            alumnoId,
            clubId,
            UUID.randomUUID(),
        )
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

    /** Fila sembrada con SQL directo, ancla el día a un plan concreto para que el reporte comparta su PK. */
    private fun sembrarFilaResuelta(dia: LocalDate): UUID {
        val planId = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO seguimiento.plan_resuelto_por_alumno
                (alumno_id, plan_id, club_id, dia, sesion_resuelta, mensaje_al_alumno, es_personalizada,
                 last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, '{"tipo":"RODAJE"}'::jsonb, NULL, FALSE, ?, ?)
            """.trimIndent(),
            alumnoId,
            planId,
            clubId,
            dia,
            UUID.randomUUID(),
            Timestamp.from(Instant.now()),
        )
        return planId
    }

    @Test
    fun `reportar HECHO con valoracion cumple el contrato y devuelve la sesion con el reporte aplicado`() {
        autenticar(ALUMNO_EMAIL)
        sembrarFilaResuelta(HOY)

        val respuesta =
            verificar(
                "/api/me/reportes/$HOY",
                "/me/reportes/{dia}",
                """{"estado":"HECHO","valoracion":4,"notas":"bien"}""",
                HttpStatus.OK,
            )

        val cuerpo = json.readTree(respuesta.body).get("reporte")
        assertEquals("HECHO", cuerpo.get("estado").asText())
        assertEquals(4, cuerpo.get("valoracion").asInt())
        assertEquals(false, cuerpo.get("marcaDolor").asBoolean())
    }

    @Test
    fun `reportar NO_HECHO con motivo MOLESTIAS activa la marca de dolor y cumple el contrato`() {
        autenticar(ALUMNO_EMAIL)
        sembrarFilaResuelta(HOY)

        val respuesta =
            verificar(
                "/api/me/reportes/$HOY",
                "/me/reportes/{dia}",
                """{"estado":"NO_HECHO","motivo":"MOLESTIAS"}""",
                HttpStatus.OK,
            )

        val cuerpo = json.readTree(respuesta.body).get("reporte")
        assertEquals("NO_HECHO", cuerpo.get("estado").asText())
        assertEquals("MOLESTIAS", cuerpo.get("motivo").asText())
        assertEquals(true, cuerpo.get("marcaDolor").asBoolean())
    }

    @Test
    fun `reportar dos veces el mismo dia edita, no duplica, y cumple el contrato`() {
        autenticar(ALUMNO_EMAIL)
        sembrarFilaResuelta(HOY)
        verificar("/api/me/reportes/$HOY", "/me/reportes/{dia}", """{"estado":"HECHO","valoracion":2}""", HttpStatus.OK)

        val respuesta =
            verificar(
                "/api/me/reportes/$HOY",
                "/me/reportes/{dia}",
                """{"estado":"HECHO","valoracion":5}""",
                HttpStatus.OK,
            )

        assertEquals(
            5,
            json
                .readTree(respuesta.body)
                .get("reporte")
                .get("valoracion")
                .asInt(),
        )
        val filas =
            jdbc.queryForObject(
                "SELECT count(*) FROM seguimiento.reporte_sesion WHERE alumno_id = ?",
                Int::class.java,
                alumnoId,
            )
        assertEquals(1, filas)
    }

    @Test
    fun `reportar un dia sin sesion publicada da 404 y cumple el contrato`() {
        autenticar(ALUMNO_EMAIL)

        val respuesta =
            verificar(
                "/api/me/reportes/$HOY",
                "/me/reportes/{dia}",
                """{"estado":"HECHO","valoracion":3}""",
                HttpStatus.NOT_FOUND,
            )

        assertEquals("NO_SESSION_THAT_DAY", json.readTree(respuesta.body).get("code").asText())
    }

    @Test
    fun `reportar un dia futuro da 400 FUTURE_DAY y cumple el contrato`() {
        autenticar(ALUMNO_EMAIL)
        // Futuro de verdad respecto al reloj real del sistema (no MutableClock aquí): HOY es una fecha fija en
        // el pasado para el resto de tests, así que sumarle un día no basta para superar la fecha de hoy.
        // +2 días, no +1: `SubmitSessionReportCommand.today()` calcula "hoy" en `Europe/Madrid`
        // (`CLUB_ZONE`), no en la zona por defecto de la JVM que ejecuta el test — en un runner en UTC,
        // entre las 22:00 y las 00:00 UTC en verano, Madrid ya está en el día siguiente y "+1" coincidiría
        // con el "hoy" del comando en vez de superarlo. "+2" es robusto sea cual sea la zona del runner.
        val futuro = LocalDate.now().plusDays(2)
        sembrarFilaResuelta(futuro)

        val respuesta =
            verificar(
                "/api/me/reportes/$futuro",
                "/me/reportes/{dia}",
                """{"estado":"HECHO","valoracion":3}""",
                HttpStatus.BAD_REQUEST,
            )

        assertEquals("FUTURE_DAY", json.readTree(respuesta.body).get("code").asText())
    }

    @Test
    fun `HECHO sin valoracion da 400 VALORACION_REQUERIDA y cumple el contrato`() {
        autenticar(ALUMNO_EMAIL)
        sembrarFilaResuelta(HOY)

        val respuesta =
            verificar("/api/me/reportes/$HOY", "/me/reportes/{dia}", """{"estado":"HECHO"}""", HttpStatus.BAD_REQUEST)

        assertEquals("VALORACION_REQUERIDA", json.readTree(respuesta.body).get("code").asText())
    }

    @Test
    fun `NO_HECHO sin motivo da 400 MOTIVO_REQUERIDO y cumple el contrato`() {
        autenticar(ALUMNO_EMAIL)
        sembrarFilaResuelta(HOY)

        val respuesta =
            verificar(
                "/api/me/reportes/$HOY",
                "/me/reportes/{dia}",
                """{"estado":"NO_HECHO"}""",
                HttpStatus.BAD_REQUEST,
            )

        assertEquals("MOTIVO_REQUERIDO", json.readTree(respuesta.body).get("code").asText())
    }

    @Test
    fun `un entrenador no puede reportar una sesion del alumno`() {
        autenticar(ENTRENADOR_EMAIL)

        val respuesta =
            verificar(
                "/api/me/reportes/$HOY",
                "/me/reportes/{dia}",
                """{"estado":"HECHO","valoracion":4}""",
                HttpStatus.FORBIDDEN,
            )

        assertEquals("FORBIDDEN", json.readTree(respuesta.body).get("code").asText())
    }

    @Test
    fun `sin consentimiento vigente da 403 CONSENTIMIENTO_NO_VIGENTE y cumple el contrato`() {
        autenticar(ALUMNO_EMAIL)
        sembrarFilaResuelta(HOY)
        jdbc.update("DELETE FROM seguimiento.consentimiento_alumno WHERE alumno_id = ?", alumnoId)

        val respuesta =
            verificar(
                "/api/me/reportes/$HOY",
                "/me/reportes/{dia}",
                """{"estado":"HECHO","valoracion":4}""",
                HttpStatus.FORBIDDEN,
            )

        assertEquals("CONSENTIMIENTO_NO_VIGENTE", json.readTree(respuesta.body).get("code").asText())
    }

    @Test
    fun `con consentimiento revocado da 403 CONSENTIMIENTO_NO_VIGENTE`() {
        autenticar(ALUMNO_EMAIL)
        sembrarFilaResuelta(HOY)
        jdbc.update("UPDATE seguimiento.consentimiento_alumno SET vigente = FALSE WHERE alumno_id = ?", alumnoId)

        val respuesta =
            verificar(
                "/api/me/reportes/$HOY",
                "/me/reportes/{dia}",
                """{"estado":"HECHO","valoracion":4}""",
                HttpStatus.FORBIDDEN,
            )

        assertEquals("CONSENTIMIENTO_NO_VIGENTE", json.readTree(respuesta.body).get("code").asText())
    }

    /** Ejecuta el PUT, comprueba el status y valida el cuerpo contra la spec. */
    private fun verificar(
        ruta: String,
        specPath: String,
        cuerpoRequest: String,
        esperado: HttpStatus,
    ): ResponseEntity<String> {
        val respuesta = putJson(ruta, cuerpoRequest)
        assertEquals(esperado, respuesta.statusCode, respuesta.body.orEmpty())
        assertContract(specPath, esperado, respuesta.body)
        return respuesta
    }

    private fun autenticar(email: String) {
        get("/api/sesion/actual") // handshake CSRF
        val login = postJson("/api/sesion", """{"email":"$email","password":"$PASSWORD"}""")
        assertEquals(HttpStatus.OK, login.statusCode, login.body.orEmpty())
    }

    private fun assertContract(
        specPath: String,
        status: HttpStatus,
        body: String?,
    ) {
        val builder = SimpleResponse.Builder(status.value())
        if (body != null) {
            builder.withBody(body).withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        }
        val report = validator.validateResponse(specPath, Request.Method.PUT, builder.build())
        val errores = report.messages.filter { it.level == ValidationReport.Level.ERROR }
        assertTrue(
            errores.isEmpty(),
            "Respuesta PUT $specPath ($status) no cumple api/openapi.yaml:\n" +
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
        private const val ALUMNO_EMAIL = "alumno-reportes-contract@runcriticon.local"
        private const val ENTRENADOR_EMAIL = "entrenador-reportes-contract@runcriticon.local"
        private const val PASSWORD = "contract-test-password-12345"

        // Fijo en vez de LocalDate.now(): igual que el resto de contratos de seguimiento, evita que el test
        // se vuelva flaky al cruzar una medianoche real durante la ejecución.
        private val HOY: LocalDate = LocalDate.parse("2026-08-17")

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
