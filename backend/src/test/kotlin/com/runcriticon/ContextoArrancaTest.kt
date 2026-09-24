package com.runcriticon

import com.runcriticon.testing.IntegrationTestBase
import org.junit.jupiter.api.Test

/**
 * Smoke de arranque del esqueleto: el contexto completo de Spring arranca contra un PostgreSQL
 * real (Testcontainers) y Flyway aplica todas las migraciones (schemas, outbox, Spring Session y
 * identidad.usuario). Valida el cableado de beans, la SecurityFilterChain, Argon2 y que
 * Hibernate (ddl-auto=validate) cuadra con el schema generado por Flyway.
 */
class ContextoArrancaTest : IntegrationTestBase() {
    @Test
    fun `el contexto arranca y Flyway aplica las migraciones sobre Postgres`() {
        // El propio arranque del @SpringBootTest es la aserción: si algún bean no cablea, la
        // SecurityFilterChain falla o Hibernate no valida el schema, el test no llega aquí.
    }
}
