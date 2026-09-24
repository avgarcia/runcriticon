package com.runcriticon.seguimiento.contracts

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.model.Request
import com.atlassian.oai.validator.model.SimpleResponse
import com.atlassian.oai.validator.report.ValidationReport
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.infrastructure.persistence.entities.UserEntity
import com.runcriticon.identidad.infrastructure.persistence.repositories.UserEntityRepository
import com.runcriticon.testing.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.nio.file.Paths
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Contrato REST runtime contra `api/openapi.yaml` para `GET /salud-del-club/actividad`. Mismo patrón que
 * `GruposOpenApiContractTest`: backend arrancado con Testcontainers, login real por HTTP, sin mocks.
 *
 * Las filas se siembran con SQL directo, no vía `ReporteRegistrado`: probar el contrato REST no necesita
 * reproducir el flujo de eventos completo.
 */
class SaludDelClubOpenApiContractTest : IntegrationTestBase() {
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
        sembrarUsuario(ADMIN_EMAIL, "ADMIN")
        sembrarUsuario(ENTRENADOR_EMAIL, "ENTRENADOR")
    }

    private fun sembrarUsuario(
        email: String,
        role: String,
    ) {
        if (usuarios.findByClubIdAndNormalizedEmail(clubId, email) != null) return
        val ahora = Instant.now()
        usuarios.save(
            UserEntity(
                id = UuidCreator.getTimeOrderedEpoch(),
                clubId = clubId,
                email = email,
                normalizedEmail = email,
                name = "Usuario contrato salud $role",
                role = role,
                passwordHash = encoder.encode(PASSWORD),
                status = "ACTIVO",
                createdAt = ahora,
                modifiedAt = ahora,
            ),
        )
    }

    /** Fila sembrada con SQL directo: aísla el contrato REST del flujo de eventos. */
    private fun sembrarReporte(
        grupoId: UUID,
        dia: LocalDate,
        reportadoEn: Instant,
    ) {
        val alumnoId = UuidCreator.getTimeOrderedEpoch()
        val planId = UuidCreator.getTimeOrderedEpoch()
        jdbc.update(
            """
            INSERT INTO seguimiento.plan_resuelto_por_alumno
                (alumno_id, plan_id, club_id, grupo_id, dia, sesion_resuelta, last_processed_event_id, last_processed_event_ts)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?)
            """.trimIndent(),
            alumnoId,
            planId,
            clubId,
            grupoId,
            dia,
            """{"tipo":"RODAJE"}""",
            UUID.randomUUID(),
            Timestamp.from(Instant.now()),
        )
        jdbc.update(
            """
            INSERT INTO seguimiento.reporte_sesion (alumno_id, plan_id, dia, club_id, estado, valoracion, reportado_en)
            VALUES (?, ?, ?, ?, 'HECHO', 3, ?)
            """.trimIndent(),
            alumnoId,
            planId,
            dia,
            clubId,
            Timestamp.from(reportadoEn),
        )
    }

    @Test
    fun `la actividad por grupo cumple el contrato y refleja lo sembrado`() {
        autenticar(ADMIN_EMAIL)
        val grupoId = UUID.randomUUID()
        sembrarReporte(grupoId, LocalDate.parse("2026-09-10"), Instant.parse("2026-09-10T09:00:00Z"))

        val respuesta = verificar(HttpMethod.GET, RUTA, SPEC_PATH, HttpStatus.OK)

        val fila =
            json.readTree(respuesta.body).get("grupos").single { it.get("grupoId").asText() == grupoId.toString() }
        assertEquals("2026-09-10T09:00:00Z", fila.get("ultimaActividadEn").asText())
    }

    @Test
    fun `sin ningun reporte la lista viene vacia y cumple el contrato`() {
        autenticar(ADMIN_EMAIL)

        val respuesta = verificar(HttpMethod.GET, RUTA, SPEC_PATH, HttpStatus.OK)

        assertTrue(json.readTree(respuesta.body).get("grupos").isEmpty)
    }

    @Test
    fun `un entrenador no puede ver la vista de salud del club`() {
        autenticar(ENTRENADOR_EMAIL)

        val respuesta = verificar(HttpMethod.GET, RUTA, SPEC_PATH, HttpStatus.FORBIDDEN)

        assertEquals("FORBIDDEN", json.readTree(respuesta.body).get("code").asText())
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
        private const val RUTA = "/api/salud-del-club/actividad"
        private const val SPEC_PATH = "/salud-del-club/actividad"
        private const val ADMIN_EMAIL = "admin-salud-contract@runcriticon.local"
        private const val ENTRENADOR_EMAIL = "entrenador-salud-contract@runcriticon.local"
        private const val PASSWORD = "contract-test-password-12345"

        private fun buildValidator(): OpenApiInteractionValidator {
            val specPath = Paths.get("../api/openapi.yaml").toAbsolutePath().normalize()
            return OpenApiInteractionValidator.createFor(specPath.toString()).build()
        }
    }
}
