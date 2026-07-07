package com.runcriticon

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.http.client.ClientHttpResponse
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Cabeceras de seguridad y parámetros de Argon2id (LAL-58): toda respuesta lleva CSP,
 * Referrer-Policy y X-Content-Type-Options; HSTS solo se emite cuando la petición llega como
 * segura (X-Forwarded-Proto del proxy, ADR-0006); y el PasswordEncoder del contexto hashea con
 * el baseline OWASP de ADR-0003 D13.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class SecurityHeadersIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    private object LaxErrorHandler : DefaultResponseErrorHandler() {
        override fun hasError(response: ClientHttpResponse) = false
    }

    private val rest = RestTemplate().apply { errorHandler = LaxErrorHandler }

    @Autowired
    lateinit var encoder: PasswordEncoder

    @Test
    fun `toda respuesta lleva CSP, Referrer-Policy y X-Content-Type-Options`() {
        for (ruta in listOf("/actuator/health", "/api/sesion/actual")) {
            val respuesta = get(ruta)
            val csp = respuesta.headers.getFirst("Content-Security-Policy")
            assertNotNull(csp, "Falta Content-Security-Policy en $ruta")
            for (directiva in listOf(
                "default-src 'self'",
                "style-src 'self' 'unsafe-inline'",
                "object-src 'none'",
                "base-uri 'self'",
                "frame-ancestors 'none'",
            )) {
                assertTrue(csp!!.contains(directiva), "Falta '$directiva' en la CSP de $ruta: $csp")
            }
            assertEquals(
                "strict-origin-when-cross-origin",
                respuesta.headers.getFirst("Referrer-Policy"),
                "Referrer-Policy incorrecta en $ruta",
            )
            assertEquals(
                "nosniff",
                respuesta.headers.getFirst("X-Content-Type-Options"),
                "X-Content-Type-Options incorrecta en $ruta",
            )
        }
    }

    @Test
    fun `HSTS se emite con X-Forwarded-Proto https`() {
        val hsts = get("/actuator/health", forwardedProto = "https").headers.getFirst("Strict-Transport-Security")
        assertNotNull(hsts, "Falta Strict-Transport-Security con X-Forwarded-Proto https")
        assertTrue(hsts!!.contains("max-age=31536000"), "max-age incorrecto en: $hsts")
        assertTrue(hsts.contains("includeSubDomains"), "Falta includeSubDomains en: $hsts")
    }

    @Test
    fun `HSTS no se emite en http plano`() {
        // Documenta el comportamiento: en local http no hay HSTS; en producción App Runner reenvía
        // X-Forwarded-Proto https y el header sale (forward-headers-strategy: framework).
        assertNull(get("/actuator/health").headers.getFirst("Strict-Transport-Security"))
    }

    @Test
    fun `el PasswordEncoder del contexto hashea con el baseline OWASP de ADR-0003 D13`() {
        val hash = encoder.encode("secreta")
        assertNotNull(hash)
        assertTrue(
            hash!!.startsWith("\$argon2id\$v=19\$m=19456,t=2,p=1\$"),
            "El hash no usa los parámetros OWASP: $hash",
        )
    }

    private fun get(
        ruta: String,
        forwardedProto: String? = null,
    ): ResponseEntity<String> {
        val headers = HttpHeaders()
        forwardedProto?.let { headers["X-Forwarded-Proto"] = it }
        return rest.exchange(
            "http://localhost:$port$ruta",
            HttpMethod.GET,
            HttpEntity<String>(null, headers),
            String::class.java,
        )
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
            registry.add("runcriticon.observability.userid-hash-salt") { "test-userid-hash-salt-not-prod" }
        }
    }
}
