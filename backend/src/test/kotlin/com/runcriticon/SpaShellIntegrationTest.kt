package com.runcriticon

import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.ResponseEntity
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/**
 * La carcasa de la SPA es pública y nada más lo es: un anónimo carga `index.html` en `/` y en cualquier enlace profundo
 * (si no, no vería ni la pantalla de login), pero la API y el actuator siguen exigiendo sesión. Sin sesión, `GET` y
 * nada más; y como `SecurityConfig` abre por negación todo `GET` fuera de `/api`, ningún controlador puede vivir
 * fuera de ahí.
 *
 * El `index.html` es el sustituto de `src/test/resources/static/`; el real lo copia el Dockerfile.
 */
class SpaShellIntegrationTest : IntegrationTestBase() {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private lateinit var mappings: RequestMappingHandlerMapping

    private object LaxErrorHandler : DefaultResponseErrorHandler() {
        override fun hasError(response: ClientHttpResponse) = false
    }

    private val rest = RestTemplate().apply { errorHandler = LaxErrorHandler }

    @Test
    fun `un anonimo carga la SPA en la raiz y en los enlaces profundos, con la CSP`() {
        for (path in listOf("/", "/mi-plan", "/mi-plan/semana", "/activacion?token=x")) {
            val response = exchange(path)
            response.statusCode.value() shouldBe 200
            response.body.shouldNotBeNull() shouldContain "<rc-root"
            response.headers.getFirst("Content-Security-Policy").shouldNotBeNull()
        }
    }

    @Test
    fun `la API y el actuator siguen exigiendo sesion, aunque la ruta no exista`() {
        for (path in listOf("/api/sesion/actual", "/api/no-existe", "/api", "/actuator/prometheus")) {
            exchange(path).statusCode.value() shouldBe 401
        }
    }

    @Test
    fun `un bundle que no existe es un 404, no index html`() {
        exchange("/chunk-inexistente.js").statusCode.value() shouldBe 404
    }

    @Test
    fun `fuera de la API solo se abre GET`() {
        exchange("/mi-plan", HttpMethod.POST).statusCode.value() shouldBeIn listOf(401, 403)
    }

    @Test
    fun `ningun controlador se mapea fuera de api, donde quedaria publico`() {
        val outsideApi =
            mappings.handlerMethods.keys
                .flatMap { it.patternValues }
                .filterNot { it.startsWith("/api/") || it == "/error" }

        outsideApi.shouldBeEmpty()
    }

    private fun exchange(
        path: String,
        method: HttpMethod = HttpMethod.GET,
    ): ResponseEntity<String> =
        rest.exchange("http://localhost:$port$path", method, HttpEntity.EMPTY, String::class.java)
}
