package com.runcriticon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.ClientHttpResponse
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Verifica por HTTP real (sin sesión) que las dos rutas de reseteo de contraseña están **expuestas
 * anónimamente** (ADR-0003 D8): el `permitAll` de `SecurityConfig` y el `@NoAuthRequired` del
 * controlador deben dejarlas pasar sin cookie. La afirmación clave es la ausencia de 401/403: la
 * solicitud responde 202 neutra y el consumo con token inválido responde 400, nunca "no autenticado".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PasswordResetAnonymousSmokeTest {
    @LocalServerPort
    private var port: Int = 0

    private object LaxErrorHandler : DefaultResponseErrorHandler() {
        override fun hasError(response: ClientHttpResponse) = false
    }

    private val rest = RestTemplate().apply { errorHandler = LaxErrorHandler }
    private val cookies = mutableMapOf<String, String>()

    @Test
    fun `la solicitud de reseteo es anonima y responde 202 neutro`() {
        // Handshake para obtener la cookie CSRF (sin sesión).
        get("/api/sesion/actual")
        assertNotNull(cookies["XSRF-TOKEN"], "El backend debe emitir la cookie XSRF-TOKEN")

        val resp = postJson("/api/sesion/reseteo", """{"email":"nadie@club.local"}""")

        assertEquals(HttpStatus.ACCEPTED, resp.statusCode, "Reseteo anónimo debe ser 202 neutro, no 401/403")
    }

    @Test
    fun `el consumo de reseteo es anonimo y con token invalido responde 400 (no 401)`() {
        get("/api/sesion/actual")

        val resp =
            postJson("/api/sesion/reseteo/consumo", """{"token":"no-existe","newPassword":"clave-clave-clave"}""")

        assertTrue(
            resp.statusCode == HttpStatus.BAD_REQUEST,
            "Consumo anónimo con token inválido debe ser 400, no 401/403 — fue ${resp.statusCode}",
        )
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
        }
    }
}
