package com.runcriticon.shared.api.rest.config

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource

/**
 * Qué devuelve el fallback de la SPA para cada ruta, contra el `static/index.html` de los recursos de test. Quién puede
 * pedirlo lo cubre `SpaShellIntegrationTest`.
 */
class SpaFallbackResourceResolverTest {
    private val resolver = SpaFallbackResourceResolver()
    private val static = ClassPathResource("static/")

    private fun resolve(path: String): Resource? = resolver.resolveResource(null, path, listOf(static), mockk())

    @Test
    fun `un fichero que existe se sirve tal cual`() {
        resolve("index.html").shouldNotBeNull().filename shouldBe "index.html"
    }

    @Test
    fun `una ruta del router de Angular devuelve index html`() {
        for (path in listOf("mi-plan", "mi-plan/semana", "activacion", "")) {
            resolve(path).shouldNotBeNull().filename shouldBe "index.html"
        }
    }

    @Test
    fun `una ruta de la API o del actuator sin controlador no devuelve index html`() {
        for (path in listOf("api", "api/no-existe", "actuator/no-existe")) {
            resolve(path).shouldBeNull()
        }
    }

    /** Un bundle de un despliegue anterior: devolver HTML haría que el navegador lo cargara como JavaScript. */
    @Test
    fun `un fichero con extension que no existe no devuelve index html`() {
        for (path in listOf("chunk-inexistente.js", "styles-viejo.css", "favicon.ico")) {
            resolve(path).shouldBeNull()
        }
    }
}
