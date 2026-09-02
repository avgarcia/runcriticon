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
import java.time.ZoneOffset
import java.util.UUID

/**
 * Contrato REST runtime contra `api/openapi.yaml` para `PUT`/`DELETE /me/reajustes/{dia}` (LAL-33). Mismo
 * patrón que `MiReportesOpenApiContractTest`: backend arrancado con Testcontainers, login real por HTTP, sin
 * mocks.
 *
 * `ORIGEN`/`DESTINO` se calculan contra `LocalDate.now(UTC)`, no un valor fijo en el pasado: a diferencia del
 * reporte de sesión (que acepta días pasados), el reajuste exige un día de hoy en adelante — el margen de +2
 * días evita el mismo cruce de medianoche `Europe/Madrid` vs `UTC` que ya documenta `MiReportesOpenApiContractTest`.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class MiReajustesOpenApiContractTest {
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
        jdbc.update("DELETE FROM seguimiento.reajuste_dia WHERE alumno_id = ?", alumnoId)
        jdbc.update("DELETE FROM seguimiento.plan_resuelto_por_alumno WHERE alumno_id = ?", alumnoId)
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

    private fun sembrarFilaResuelta(
        dia: LocalDate,
        tipo: String = "RODAJE",
    ): UUID {
        val planId = UUID.randomUUID()
        jdbc.update(
            """
            INSERT INTO seguimiento.plan_resuelto_por_alumno
                (alumno_id, plan_id, club_id, dia, sesion_resuelta, mensaje_al_alumno, es_personalizada,
                 last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, ?::jsonb, NULL, FALSE, ?, ?)
            """.trimIndent(),
            alumnoId,
            planId,
            clubId,
            dia,
            """{"tipo":"$tipo"}""",
            UUID.randomUUID(),
            Timestamp.from(Instant.now()),
        )
        return planId
    }

    @Test
    fun `mover a un dia libre cumple el contrato y devuelve el reajuste aplicado`() {
        autenticar(ALUMNO_EMAIL)
        sembrarFilaResuelta(ORIGEN)

        val respuesta =
            verificarPut(
                "/api/me/reajustes/$ORIGEN",
                "/me/reajustes/{dia}",
                """{"accion":"MOVIDA","diaDestino":"$DESTINO","motivo":"CANSANCIO"}""",
                HttpStatus.OK,
            )

        val cuerpo = json.readTree(respuesta.body)
        assertEquals("MOVIDA", cuerpo.get("accion").asText())
        assertEquals(DESTINO.toString(), cuerpo.get("diaDestino").asText())
        assertEquals(false, cuerpo.get("marcaDolor").asBoolean())
    }

    @Test
    fun `saltar con motivo MOLESTIAS activa la marca de dolor y cumple el contrato`() {
        autenticar(ALUMNO_EMAIL)
        sembrarFilaResuelta(ORIGEN)

        val respuesta =
            verificarPut(
                "/api/me/reajustes/$ORIGEN",
                "/me/reajustes/{dia}",
                """{"accion":"SALTADA","motivo":"MOLESTIAS"}""",
                HttpStatus.OK,
            )

        val cuerpo = json.readTree(respuesta.body)
        assertEquals("SALTADA", cuerpo.get("accion").asText())
        assertEquals(true, cuerpo.get("marcaDolor").asBoolean())
    }

    @Test
    fun `reajustar dos veces el mismo dia edita, no duplica`() {
        autenticar(ALUMNO_EMAIL)
        sembrarFilaResuelta(ORIGEN)
        verificarPut(
            "/api/me/reajustes/$ORIGEN",
            "/me/reajustes/{dia}",
            """{"accion":"SALTADA","motivo":"CANSANCIO"}""",
            HttpStatus.OK,
        )

        verificarPut(
            "/api/me/reajustes/$ORIGEN",
            "/me/reajustes/{dia}",
            """{"accion":"MOVIDA","diaDestino":"$DESTINO","motivo":"IMPREVISTO"}""",
            HttpStatus.OK,
        )

        val filas =
            jdbc.queryForObject(
                "SELECT count(*) FROM seguimiento.reajuste_dia WHERE alumno_id = ?",
                Int::class.java,
                alumnoId,
            )
        assertEquals(1, filas)
    }

    @Test
    fun `reajustar un dia sin sesion publicada da 404 y cumple el contrato`() {
        autenticar(ALUMNO_EMAIL)

        val respuesta =
            verificarPut(
                "/api/me/reajustes/$ORIGEN",
                "/me/reajustes/{dia}",
                """{"accion":"SALTADA","motivo":"CANSANCIO"}""",
                HttpStatus.NOT_FOUND,
            )

        assertEquals("NO_SESSION_THAT_DAY", json.readTree(respuesta.body).get("code").asText())
    }

    @Test
    fun `reajustar un dia pasado da 400 DIA_PASADO y cumple el contrato`() {
        autenticar(ALUMNO_EMAIL)
        val pasado = LocalDate.now(ZoneOffset.UTC).minusDays(3)
        sembrarFilaResuelta(pasado)

        val respuesta =
            verificarPut(
                "/api/me/reajustes/$pasado",
                "/me/reajustes/{dia}",
                """{"accion":"SALTADA","motivo":"CANSANCIO"}""",
                HttpStatus.BAD_REQUEST,
            )

        assertEquals("DIA_PASADO", json.readTree(respuesta.body).get("code").asText())
    }

    @Test
    fun `un destino a mas de 7 dias da 400 DESTINO_FUERA_DE_RANGO y cumple el contrato`() {
        autenticar(ALUMNO_EMAIL)
        sembrarFilaResuelta(ORIGEN)
        val fueraDeRango = ORIGEN.plusDays(9)

        val respuesta =
            verificarPut(
                "/api/me/reajustes/$ORIGEN",
                "/me/reajustes/{dia}",
                """{"accion":"MOVIDA","diaDestino":"$fueraDeRango","motivo":"CANSANCIO"}""",
                HttpStatus.BAD_REQUEST,
            )

        assertEquals("DESTINO_FUERA_DE_RANGO", json.readTree(respuesta.body).get("code").asText())
    }

    @Test
    fun `destino ocupado sin resolucionConflicto da 409 DIA_DESTINO_OCUPADO y cumple el contrato`() {
        autenticar(ALUMNO_EMAIL)
        sembrarFilaResuelta(ORIGEN)
        sembrarFilaResuelta(DESTINO, tipo = "SERIES")

        val respuesta =
            verificarPut(
                "/api/me/reajustes/$ORIGEN",
                "/me/reajustes/{dia}",
                """{"accion":"MOVIDA","diaDestino":"$DESTINO","motivo":"CANSANCIO"}""",
                HttpStatus.CONFLICT,
            )

        assertEquals("DIA_DESTINO_OCUPADO", json.readTree(respuesta.body).get("code").asText())
    }

    @Test
    fun `destino ocupado con REEMPLAZAR cumple el contrato y mueve la sesion de origen`() {
        autenticar(ALUMNO_EMAIL)
        sembrarFilaResuelta(ORIGEN)
        sembrarFilaResuelta(DESTINO, tipo = "SERIES")

        val cuerpo =
            """{"accion":"MOVIDA","diaDestino":"$DESTINO","motivo":"CANSANCIO",""" +
                """"resolucionConflicto":"REEMPLAZAR"}"""
        val respuesta = verificarPut("/api/me/reajustes/$ORIGEN", "/me/reajustes/{dia}", cuerpo, HttpStatus.OK)

        assertEquals("MOVIDA", json.readTree(respuesta.body).get("accion").asText())
        val filas =
            jdbc.queryForObject(
                "SELECT count(*) FROM seguimiento.reajuste_dia WHERE alumno_id = ?",
                Int::class.java,
                alumnoId,
            )
        assertEquals(2, filas)
    }

    @Test
    fun `un entrenador no puede reajustar una sesion del alumno`() {
        autenticar(ENTRENADOR_EMAIL)

        val respuesta =
            verificarPut(
                "/api/me/reajustes/$ORIGEN",
                "/me/reajustes/{dia}",
                """{"accion":"SALTADA","motivo":"CANSANCIO"}""",
                HttpStatus.FORBIDDEN,
            )

        assertEquals("FORBIDDEN", json.readTree(respuesta.body).get("code").asText())
    }

    @Test
    fun `sin consentimiento vigente da 403 CONSENTIMIENTO_NO_VIGENTE y cumple el contrato`() {
        autenticar(ALUMNO_EMAIL)
        sembrarFilaResuelta(ORIGEN)
        jdbc.update("DELETE FROM seguimiento.consentimiento_alumno WHERE alumno_id = ?", alumnoId)

        val respuesta =
            verificarPut(
                "/api/me/reajustes/$ORIGEN",
                "/me/reajustes/{dia}",
                """{"accion":"SALTADA","motivo":"CANSANCIO"}""",
                HttpStatus.FORBIDDEN,
            )

        assertEquals("CONSENTIMIENTO_NO_VIGENTE", json.readTree(respuesta.body).get("code").asText())
    }

    @Test
    fun `deshacer un reajuste existente da 204 y cumple el contrato`() {
        autenticar(ALUMNO_EMAIL)
        sembrarFilaResuelta(ORIGEN)
        verificarPut(
            "/api/me/reajustes/$ORIGEN",
            "/me/reajustes/{dia}",
            """{"accion":"SALTADA","motivo":"CANSANCIO"}""",
            HttpStatus.OK,
        )

        verificarDelete("/api/me/reajustes/$ORIGEN", "/me/reajustes/{dia}", HttpStatus.NO_CONTENT)

        val filas =
            jdbc.queryForObject(
                "SELECT count(*) FROM seguimiento.reajuste_dia WHERE alumno_id = ?",
                Int::class.java,
                alumnoId,
            )
        assertEquals(0, filas)
    }

    @Test
    fun `deshacer un dia sin reajuste es idempotente, da 204 y cumple el contrato`() {
        autenticar(ALUMNO_EMAIL)
        sembrarFilaResuelta(ORIGEN)

        verificarDelete("/api/me/reajustes/$ORIGEN", "/me/reajustes/{dia}", HttpStatus.NO_CONTENT)
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
        val respuesta = intercambiar(ruta, HttpMethod.DELETE, null)
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
        private const val ALUMNO_EMAIL = "alumno-reajustes-contract@runcriticon.local"
        private const val ENTRENADOR_EMAIL = "entrenador-reajustes-contract@runcriticon.local"
        private const val PASSWORD = "contract-test-password-12345"

        // ORIGEN/DESTINO contra LocalDate.now(UTC), no fijos: a diferencia del reporte de sesión, el reajuste
        // exige un día de hoy en adelante. +2/+4 dan margen frente al cruce de medianoche Europe/Madrid vs UTC
        // (RescheduleDayCommand.today() resuelve en Europe/Madrid).
        private val ORIGEN: LocalDate = LocalDate.now(ZoneOffset.UTC).plusDays(2)
        private val DESTINO: LocalDate = ORIGEN.plusDays(2)

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
