package com.runcriticon

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.infrastructure.persistence.entities.UserEntity
import com.runcriticon.identidad.infrastructure.persistence.repositories.UserEntityRepository
import com.runcriticon.testing.IntegrationTestBase
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.UUID

/**
 * Los endpoints de Actuator que no son de salud solo son accesibles con rol ADMIN. Sin una regla de rol sobre la ruta,
 * cae en `authenticated()` y cualquier usuario con sesión, alumno incluido, la alcanza:
 * - `/actuator/loggers` (cambio de nivel de log en runtime, ADR-0013 D9): un alumno podía cambiar los niveles de log
 *   en producción.
 * - `/actuator/prometheus` (métricas operativas, ADR-0011 D1): un alumno podía leer la carga, los errores y los
 *   nombres internos del sistema.
 */
class ActuatorAdminEndpointsAuthorizationIntegrationTest : IntegrationTestBase() {
    @LocalServerPort
    private var port: Int = 0

    private object LaxErrorHandler : DefaultResponseErrorHandler() {
        override fun hasError(response: ClientHttpResponse) = false
    }

    private val rest = RestTemplate().apply { errorHandler = LaxErrorHandler }

    @Autowired
    lateinit var usuarios: UserEntityRepository

    @Autowired
    lateinit var encoder: PasswordEncoder

    private val cookies = mutableMapOf<String, String>()

    @BeforeEach
    fun sembrarUsuarios() {
        sembrar(ADMIN_EMAIL, "ADMIN")
        sembrar(ALUMNO_EMAIL, "ALUMNO")
    }

    private fun sembrar(
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
                name = "Test $role",
                role = role,
                passwordHash = encoder.encode(PASSWORD),
                status = "ACTIVO",
                createdAt = ahora,
                modifiedAt = ahora,
            ),
        )
    }

    @Test
    fun `admin puede leer actuator loggers`() {
        login(ADMIN_EMAIL)
        assertEquals(HttpStatus.OK, get("/actuator/loggers").statusCode)
    }

    @Test
    fun `alumno recibe 403 en actuator loggers`() {
        login(ALUMNO_EMAIL)
        println("DEBUG cookies=$cookies")
        val resp = get("/actuator/loggers")
        println("DEBUG status=${resp.statusCode} body=${resp.body}")
        assertEquals(HttpStatus.FORBIDDEN, resp.statusCode)
    }

    @Test
    fun `anonimo recibe 401 en actuator loggers`() {
        assertEquals(HttpStatus.UNAUTHORIZED, get("/actuator/loggers").statusCode)
    }

    @Test
    fun `admin puede leer actuator prometheus`() {
        login(ADMIN_EMAIL)
        assertEquals(HttpStatus.OK, get("/actuator/prometheus").statusCode)
    }

    @Test
    fun `alumno recibe 403 en actuator prometheus`() {
        login(ALUMNO_EMAIL)
        assertEquals(HttpStatus.FORBIDDEN, get("/actuator/prometheus").statusCode)
    }

    @Test
    fun `anonimo recibe 401 en actuator prometheus`() {
        assertEquals(HttpStatus.UNAUTHORIZED, get("/actuator/prometheus").statusCode)
    }

    private fun login(email: String) {
        get("/api/sesion/actual")
        val login = postJson("/api/sesion", """{"email":"$email","password":"$PASSWORD"}""")
        assertEquals(HttpStatus.OK, login.statusCode, login.body.orEmpty())
    }

    private fun get(ruta: String): ResponseEntity<String> = intercambiar(ruta, HttpMethod.GET, null)

    private fun postJson(
        ruta: String,
        cuerpo: String,
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
            rest.exchange(
                "http://localhost:$port$ruta",
                metodo,
                HttpEntity(cuerpo, headers),
                String::class.java,
            )
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
        private const val ADMIN_EMAIL = "admin.loggers@runcriticon.local"
        private const val ALUMNO_EMAIL = "alumno.loggers@runcriticon.local"
        private const val PASSWORD = "loggers-password-12345"
    }
}
