package com.runcriticon

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Smoke de arranque del esqueleto: el contexto completo de Spring arranca contra un PostgreSQL
 * real (Testcontainers) y Flyway aplica todas las migraciones (schemas, outbox, Spring Session y
 * identidad.usuario). Valida el cableado de beans, la SecurityFilterChain, Argon2 y que
 * Hibernate (ddl-auto=validate) cuadra con el schema generado por Flyway.
 */
@SpringBootTest
@Testcontainers
class ContextoArrancaTest {
    @Test
    fun `el contexto arranca y Flyway aplica las migraciones sobre Postgres`() {
        // El propio arranque del @SpringBootTest es la aserción: si algún bean no cablea, la
        // SecurityFilterChain falla o Hibernate no valida el schema, el test no llega aquí.
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
        }
    }
}
