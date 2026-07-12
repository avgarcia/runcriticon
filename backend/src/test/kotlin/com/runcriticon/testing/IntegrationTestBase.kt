package com.runcriticon.testing

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Base para tests de integración con Postgres real (ADR-0010, configuracion-y-secretos-en-modulos.md
 * §10). Carga `application-test.yml` (perfil `test`) para los secretos estáticos
 * (`token-hmac-secret`, `userid-hash-salt`); el datasource se inyecta vía [DynamicPropertySource]
 * porque Testcontainers asigna el puerto en tiempo de ejecución — no puede ser un valor estático del
 * YAML.
 *
 * Nueva infraestructura, no retroactiva: los tests de integración existentes siguen declarando su
 * propio `@Container`/`@DynamicPropertySource` (funcionan, migrarlos es un refactor aparte).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
// abstract (no object): cada subclase necesita su propia clase para que @SpringBootTest levante
// un contexto Spring por test — un object no puede subclasificarse.
@Suppress("UtilityClassWithPublicConstructor")
abstract class IntegrationTestBase {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun datasourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }
}
