package com.runcriticon.testing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.client.RestTemplate

/**
 * Prueba que [IntegrationTestBase] arranca de verdad: el perfil `test` carga
 * `application-test.yml` (secretos estáticos) y el datasource se resuelve contra el Postgres real
 * de Testcontainers (puerto dinámico vía [org.springframework.test.context.DynamicPropertySource]).
 */
class IntegrationTestBaseTest : IntegrationTestBase() {
    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `el contexto arranca sobre el perfil test y responde en actuator health`() {
        val response = RestTemplate().getForEntity("http://localhost:$port/actuator/health", String::class.java)

        assertEquals(200, response.statusCode.value())
    }
}
