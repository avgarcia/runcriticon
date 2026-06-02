package com.runcriticon

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.infrastructure.UsuarioEntity
import com.runcriticon.identidad.infrastructure.UsuarioJpaRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

/**
 * Smoke de H0 (esqueleto andante): valida el flujo de login completo por HTTP real contra un
 * PostgreSQL de Testcontainers — handshake CSRF, login con contraseña, lectura de la sesión y
 * cierre con revocación (ADR-0003 D5/D10/D11/D14). Sella el "se puede iniciar sesión y ver una
 * pantalla" del Hito H0.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class LoginSmokeTest {
    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var usuarios: UsuarioJpaRepository

    @Autowired
    lateinit var encoder: PasswordEncoder

    /** Cookie jar: acumula y actualiza las cookies entre peticiones, como un navegador. */
    private val cookies = mutableMapOf<String, String>()

    @BeforeEach
    fun sembrarAdmin() {
        if (usuarios.findByClubIdAndEmailNormalizado(clubId, EMAIL) != null) return
        val ahora = Instant.now()
        usuarios.save(
            UsuarioEntity(
                id = UuidCreator.getTimeOrderedEpoch(),
                clubId = clubId,
                email = EMAIL,
                emailNormalizado = EMAIL,
                nombre = "Admin Smoke",
                rol = "ADMIN",
                passwordHash = encoder.encode(PASSWORD),
                estado = "ACTIVO",
                creadoEn = ahora,
                modificadoEn = ahora,
            ),
        )
    }

    @Test
    fun `login end-to-end con handshake CSRF, sesion y cierre`() {
        // 1. Handshake: sin sesión devuelve 401 pero emite la cookie XSRF-TOKEN (CsrfCookieFilter).
        val handshake = get("/api/sesion/actual")
        assertEquals(HttpStatus.UNAUTHORIZED, handshake.statusCode)
        assertNotNull(cookies["XSRF-TOKEN"], "El backend debe emitir la cookie XSRF-TOKEN")

        // 2. Login con el token CSRF y las cookies del handshake.
        val login = postJson("/api/sesion", """{"email":"$EMAIL","password":"$PASSWORD"}""")
        assertEquals(HttpStatus.OK, login.statusCode, login.body.orEmpty())
        assertTrue(login.body?.contains("ADMIN") == true, "El login devuelve el principal")

        // 3. Con la sesión establecida, /actual responde el principal.
        val actual = get("/api/sesion/actual")
        assertEquals(HttpStatus.OK, actual.statusCode)
        assertTrue(actual.body?.contains("ADMIN") == true)

        // 4. Cierre de sesión.
        val cierre = postJson("/api/sesion/cierre", "{}")
        assertEquals(HttpStatus.NO_CONTENT, cierre.statusCode)

        // 5. Tras el cierre, la sesión está revocada: /actual vuelve a 401.
        val trasCierre = get("/api/sesion/actual")
        assertEquals(HttpStatus.UNAUTHORIZED, trasCierre.statusCode)
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
            headers[HttpHeaders.COOKIE] = cookies.map { (nombre, valor) -> "$nombre=$valor" }
        }
        if (metodo != HttpMethod.GET) {
            headers.contentType = MediaType.APPLICATION_JSON
            cookies["XSRF-TOKEN"]?.let { headers["X-XSRF-TOKEN"] = it }
        }
        val respuesta = rest.exchange(ruta, metodo, HttpEntity(cuerpo, headers), String::class.java)
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
        private const val EMAIL = "admin@runcriticon.local"
        private const val PASSWORD = "smoke-password-12345"

        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun propiedades(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            // Solo en el test: que el cuerpo del 500 incluya mensaje y stacktrace para diagnóstico.
            registry.add("server.error.include-message") { "always" }
            registry.add("server.error.include-stacktrace") { "always" }
        }
    }
}
