package com.runcriticon.identidad.contracts

import com.runcriticon.testing.IntegrationTestBase
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.parser.OpenAPIV3Parser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.nio.file.Paths

/**
 * Toda operación que `api/openapi.yaml` declara anónima (`security: []`) es alcanzable sin sesión a través de la
 * cadena de seguridad real. La SPA se sirve desde la propia app (ADR-0001): si una de estas operaciones cae en
 * `anyRequest().authenticated()`, el invitado que abre el enlace de su email recibe un 401 y el interceptor del
 * frontend lo manda al login, sin que ningún test de caso de uso ni de controlador lo vea.
 *
 * El 401 que fabrica la cadena de seguridad no lleva cuerpo; el de la aplicación sí (`{code, message}`, p. ej.
 * credenciales erróneas en el login). Con una petición vacía o inválida, lo esperable de una operación permitida es
 * un 4xx con cuerpo; un 401 o un 403 sin cuerpo es que no llegó al controlador.
 */
class AnonymousEndpointsReachableContractTest : IntegrationTestBase() {
    @LocalServerPort
    private var port: Int = 0

    private object LaxErrorHandler : DefaultResponseErrorHandler() {
        override fun hasError(response: ClientHttpResponse) = false
    }

    private val rest = RestTemplate().apply { errorHandler = LaxErrorHandler }

    @TestFactory
    fun `cada operacion anonima del contrato llega al controlador sin sesion`(): List<DynamicTest> {
        val anonymous = anonymousOperations()
        // Una spec que de repente no trae ninguna operación anónima haría pasar el test sin comprobar nada.
        assertTrue(
            anonymous.any { it.method == HttpMethod.POST && it.path == "/sesion" },
            "no se encontró el login entre las operaciones anónimas de api/openapi.yaml: $anonymous",
        )
        val xsrf = handshake()
        return anonymous.map { operation ->
            dynamicTest("${operation.method} /api${operation.path}") {
                val response = send(operation, xsrf)
                val blockedBySecurityChain =
                    response.body.isNullOrEmpty() &&
                        response.statusCode in setOf(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)
                assertFalse(
                    blockedBySecurityChain,
                    "${operation.method} /api${operation.path} es anónima en api/openapi.yaml pero la cadena de " +
                        "seguridad la rechaza con ${response.statusCode} sin cuerpo: " +
                        "falta su `permitAll` en SecurityConfig",
                )
            }
        }
    }

    /** Cookie CSRF que deja cualquier primera petición; sin ella, las escrituras anónimas fallan por CSRF. */
    private fun handshake(): String {
        val response = rest.getForEntity("http://localhost:$port/api/sesion/actual", String::class.java)
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        val setCookie =
            response.headers[HttpHeaders.SET_COOKIE].orEmpty().firstOrNull { it.startsWith("XSRF-TOKEN=") }
        return requireNotNull(setCookie) { "el handshake no dejó la cookie XSRF-TOKEN" }
            .substringBefore(";")
            .substringAfter("=")
    }

    private fun send(
        operation: AnonymousOperation,
        xsrf: String,
    ): ResponseEntity<String> {
        val headers = HttpHeaders()
        headers[HttpHeaders.COOKIE] = "XSRF-TOKEN=$xsrf"
        headers["X-XSRF-TOKEN"] = xsrf
        headers.contentType = MediaType.APPLICATION_JSON
        val body = if (operation.method == HttpMethod.GET) null else "{}"
        return rest.exchange(
            "http://localhost:$port/api${operation.url}",
            operation.method,
            HttpEntity(body, headers),
            String::class.java,
        )
    }

    private data class AnonymousOperation(
        val method: HttpMethod,
        val path: String,
        val url: String,
    )

    private fun anonymousOperations(): List<AnonymousOperation> {
        val spec =
            Paths
                .get("../api/openapi.yaml")
                .toAbsolutePath()
                .normalize()
                .toString()
        val openApi = requireNotNull(OpenAPIV3Parser().read(spec)) { "no se pudo leer $spec" }
        return openApi.paths.orEmpty().flatMap { (path, item) ->
            operationsOf(item)
                .filter { (_, operation) -> operation.security?.isEmpty() == true }
                .map { (method, operation) -> AnonymousOperation(method, path, urlWithPlaceholders(path, operation)) }
        }
    }

    private fun operationsOf(item: PathItem): List<Pair<HttpMethod, Operation>> =
        listOfNotNull(
            item.get?.let { HttpMethod.GET to it },
            item.post?.let { HttpMethod.POST to it },
            item.put?.let { HttpMethod.PUT to it },
            item.patch?.let { HttpMethod.PATCH to it },
            item.delete?.let { HttpMethod.DELETE to it },
        )

    /** Ruta con valores de relleno para los parámetros de ruta y los de query obligatorios. */
    private fun urlWithPlaceholders(
        path: String,
        operation: Operation,
    ): String {
        val concrete = path.replace(Regex("\\{[^}]+}"), PLACEHOLDER_ID)
        val required = operation.parameters.orEmpty().filter { it.`in` == "query" && it.required == true }
        return if (required.isEmpty()) concrete else concrete + "?" + required.joinToString("&") { "${it.name}=x" }
    }

    private companion object {
        const val PLACEHOLDER_ID = "00000000-0000-0000-0000-000000000000"
    }
}
