package com.runcriticon.testing

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Base para tests de integración con Postgres real (ADR-0010, configuracion-y-secretos-en-modulos.md
 * §10). Carga `application-test.yml` (perfil `test`) para los secretos estáticos
 * (`token-hmac-secret`, `userid-hash-salt`); el datasource se inyecta vía [DynamicPropertySource]
 * porque Testcontainers asigna el puerto en tiempo de ejecución — no puede ser un valor estático del
 * YAML.
 *
 * Nueva infraestructura, no retroactiva: los tests de integración existentes siguen declarando su
 * propio contenedor y `@DynamicPropertySource` (funcionan, migrarlos es un refactor aparte).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
// abstract (no object): cada subclase necesita su propia clase para que @SpringBootTest levante
// un contexto Spring por test — un object no puede subclasificarse.
@Suppress("UtilityClassWithPublicConstructor")
abstract class IntegrationTestBase {
    companion object {
        /**
         * **Patrón *singleton container***: se arranca a mano en la inicialización del companion y esta clase **no**
         * lleva `@Testcontainers`.
         *
         * La extensión de JUnit gestiona el ciclo de vida del contenedor **por clase de test**, y eso no vale para
         * uno compartido entre subclases: como todas tienen la misma configuración, Spring reutiliza el contexto
         * cacheado, y su datasource acaba apuntando a un contenedor que ya no escucha — `Connection refused` y health
         * `DOWN` en la última clase que se ejecuta. Con dos subclases el fallo ya era reproducible; se arregla
         * sacando el ciclo de vida de manos de la extensión.
         *
         * Así el contenedor vive lo que dura la JVM de tests y lo retira Ryuk al salir. Coste asumido: si `start()`
         * falla, el error sale como `ExceptionInInitializerError` en vez de por el informe de la extensión.
         *
         * Un test de integración con **contenedor propio** (no compartido) sí debe usar `@Testcontainers` +
         * `@Container`: ahí el ciclo de vida por clase es el correcto.
         */
        @JvmStatic
        val postgres: PostgreSQLContainer<Nothing> =
            PostgreSQLContainer<Nothing>("postgres:16-alpine").apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun datasourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }
}
