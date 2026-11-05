package com.runcriticon.identidad.contracts

import com.atlassian.oai.validator.OpenApiInteractionValidator
import com.atlassian.oai.validator.model.Request
import com.atlassian.oai.validator.model.SimpleResponse
import com.atlassian.oai.validator.report.ValidationReport
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.infrastructure.persistence.entities.UserEntity
import com.runcriticon.identidad.infrastructure.persistence.repositories.UserEntityRepository
import com.runcriticon.testing.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.nio.file.Paths
import java.time.Instant
import java.util.UUID

/**
 * Contrato REST runtime contra `api/openapi.yaml` para la baja con gracia: `POST`/`DELETE /api/usuarios/{id}/baja` y el
 * estado `BAJA` en `GET /api/entrenadores`. Mismo patrón que [ClubOpenApiContractTest].
 */
class UsuariosBajaOpenApiContractTest : IntegrationTestBase() {
    @LocalServerPort
    private var port: Int = 0

    private object LaxErrorHandler : DefaultResponseErrorHandler() {
        override fun hasError(response: ClientHttpResponse) = false
    }

    private val rest = RestTemplate(JdkClientHttpRequestFactory()).apply { errorHandler = LaxErrorHandler }
    private val cookies = mutableMapOf<String, String>()

    private val validator: OpenApiInteractionValidator = buildValidator()

    @Autowired
    lateinit var usuarios: UserEntityRepository

    @Autowired
    lateinit var encoder: PasswordEncoder

    @Test
    fun `marcar y cancelar la baja, y listar un entrenador en baja, cumplen el contrato OpenAPI`() {
        sembrarAdmin()
        val entrenador = sembrarEntrenador()
        get("/api/sesion/actual") // handshake CSRF
        val login = enviar("/api/sesion", HttpMethod.POST, """{"email":"$EMAIL","password":"$PASSWORD"}""")
        assertEquals(HttpStatus.OK, login.statusCode, login.body.orEmpty())

        val marcar = enviar("/api/usuarios/$entrenador/baja", HttpMethod.POST)
        assertEquals(HttpStatus.NO_CONTENT, marcar.statusCode, marcar.body.orEmpty())
        assertContract(Request.Method.POST, "/usuarios/$entrenador/baja", HttpStatus.NO_CONTENT, marcar.body)

        val listado = get("/api/entrenadores")
        assertEquals(HttpStatus.OK, listado.statusCode, listado.body.orEmpty())
        assertTrue(listado.body.orEmpty().contains("\"BAJA\""), "El listado debe traer al entrenador en BAJA")
        assertContract(Request.Method.GET, "/entrenadores", HttpStatus.OK, listado.body)

        val cancelar = enviar("/api/usuarios/$entrenador/baja", HttpMethod.DELETE)
        assertEquals(HttpStatus.NO_CONTENT, cancelar.statusCode, cancelar.body.orEmpty())
        assertContract(Request.Method.DELETE, "/usuarios/$entrenador/baja", HttpStatus.NO_CONTENT, cancelar.body)

        val repetir = enviar("/api/usuarios/$entrenador/baja", HttpMethod.DELETE)
        assertEquals(HttpStatus.CONFLICT, repetir.statusCode, repetir.body.orEmpty())
        assertContract(Request.Method.DELETE, "/usuarios/$entrenador/baja", HttpStatus.CONFLICT, repetir.body)
    }

    private fun sembrarAdmin() {
        if (usuarios.findByClubIdAndNormalizedEmail(clubId, EMAIL) != null) return
        sembrar(EMAIL, "ADMIN", encoder.encode(PASSWORD))
    }

    private fun sembrarEntrenador(): UUID =
        sembrar(email = "entrenador-baja-${UUID.randomUUID()}@runcriticon.local", rol = "ENTRENADOR")

    private fun sembrar(
        email: String,
        rol: String,
        passwordHash: String? = null,
    ): UUID {
        val id = UuidCreator.getTimeOrderedEpoch()
        val ahora = Instant.now()
        usuarios.save(
            UserEntity(
                id = id,
                clubId = clubId,
                email = email,
                normalizedEmail = email,
                name = "Contrato Baja",
                role = rol,
                passwordHash = passwordHash,
                status = "ACTIVO",
                createdAt = ahora,
                modifiedAt = ahora,
            ),
        )
        return id
    }

    private fun assertContract(
        method: Request.Method,
        specPath: String,
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

    private fun get(ruta: String): ResponseEntity<String> = enviar(ruta, HttpMethod.GET)

    private fun enviar(
        ruta: String,
        metodo: HttpMethod,
        cuerpo: String? = null,
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
        respuesta.headers[HttpHeaders.SET_COOKIE]?.forEach { setCookie ->
            val par = setCookie.substringBefore(";")
            val nombre = par.substringBefore("=")
            val valor = par.substringAfter("=")
            if (valor.isBlank()) cookies.remove(nombre) else cookies[nombre] = valor
        }
        return respuesta
    }

    companion object {
        private val clubId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        private const val EMAIL = "admin-baja-contract@runcriticon.local"
        private const val PASSWORD = "contract-test-password-12345"

        private fun buildValidator(): OpenApiInteractionValidator {
            val specPath = Paths.get("../api/openapi.yaml").toAbsolutePath().normalize()
            return OpenApiInteractionValidator.createFor(specPath.toString()).build()
        }
    }
}
