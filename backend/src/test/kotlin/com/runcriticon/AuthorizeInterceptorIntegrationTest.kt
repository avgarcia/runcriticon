package com.runcriticon

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.infrastructure.persistence.entities.UserEntity
import com.runcriticon.identidad.infrastructure.persistence.repositories.UserEntityRepository
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.client.ClientHttpResponse
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.UUID

/**
 * `AuthorizeInterceptor` por HTTP real: que está registrado en la cadena de Spring MVC, que decide antes de que el
 * handler lea sus argumentos y que su denegación llega al cliente como el 403 neutro de siempre y al outbox como un
 * `AccesoDenegado`. La lógica del motor la cubre `AuthorizeInterceptorTest`.
 *
 * Usa `DELETE /api/usuarios/{id}` (`USER:DELETE`, solo ADMIN) con un entrenador, que no tiene ese permiso.
 */
class AuthorizeInterceptorIntegrationTest : IntegrationTestBase() {
    @LocalServerPort private var port: Int = 0

    @Autowired private lateinit var usuarios: UserEntityRepository

    @Autowired private lateinit var encoder: PasswordEncoder

    @Autowired private lateinit var jdbc: JdbcTemplate

    private object LaxErrorHandler : DefaultResponseErrorHandler() {
        override fun hasError(response: ClientHttpResponse) = false
    }

    private val rest = RestTemplate().apply { errorHandler = LaxErrorHandler }

    @Test
    fun `un rol sin el permiso recibe el 403 neutro, el caso de uso no actua y queda un AccesoDenegado`() {
        val entrenador = sembrarUsuario("ENTRENADOR")
        val alumno = sembrarUsuario("ALUMNO")
        val sesion = login(entrenador)

        val respuesta = peticion(HttpMethod.DELETE, "/api/usuarios/${alumno.id}", sesion)

        respuesta.statusCode shouldBe HttpStatus.FORBIDDEN
        respuesta.body.orEmpty() shouldContain "\"code\":\"FORBIDDEN\""
        usuarios.findById(alumno.id).isPresent shouldBe true
        denegacionesEnElOutbox(entrenador.id, "USER:DELETE") shouldBe 1
    }

    /**
     * Sin el motor, un id que no es un UUID fallaría al convertir el argumento del handler (400) sin llegar a ninguna
     * comprobación de rol. Que el entrenador reciba 403 demuestra que el motor está en la cadena y decide antes.
     */
    @Test
    fun `el motor decide antes de leer los argumentos del handler`() {
        val entrenador = sembrarUsuario("ENTRENADOR")
        val sesion = login(entrenador)

        val respuesta = peticion(HttpMethod.DELETE, "/api/usuarios/no-es-un-uuid", sesion)

        respuesta.statusCode shouldBe HttpStatus.FORBIDDEN
    }

    @Test
    fun `con el permiso, el mismo id mal formado sigue siendo un 400`() {
        val admin = sembrarUsuario("ADMIN")
        val sesion = login(admin)

        val respuesta = peticion(HttpMethod.DELETE, "/api/usuarios/no-es-un-uuid", sesion)

        respuesta.statusCode shouldBe HttpStatus.BAD_REQUEST
    }

    private fun denegacionesEnElOutbox(
        actorId: UUID,
        recurso: String,
    ): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM event_publication " +
                "WHERE event_type LIKE '%AccesoDenegado' AND serialized_event LIKE ? AND serialized_event LIKE ?",
            Int::class.java,
            "%$actorId%",
            "%$recurso%",
        ) ?: 0

    private data class Usuario(
        val id: UUID,
        val email: String,
    )

    private fun sembrarUsuario(rol: String): Usuario {
        val id = UuidCreator.getTimeOrderedEpoch()
        val ahora = Instant.now()
        val email = "motor-$id@runcriticon.local"
        usuarios.save(
            UserEntity(
                id = id,
                clubId = CLUB_ID,
                email = email,
                normalizedEmail = email,
                name = "Persona Motor",
                role = rol,
                passwordHash = encoder.encode(PASSWORD),
                status = "ACTIVO",
                createdAt = ahora,
                modifiedAt = ahora,
            ),
        )
        return Usuario(id, email)
    }

    private fun peticion(
        metodo: HttpMethod,
        ruta: String,
        sesion: String,
    ): ResponseEntity<String> {
        val xsrf = handshakeXsrf(sesion)
        val headers = HttpHeaders()
        headers[HttpHeaders.COOKIE] = "SESSION=$sesion; XSRF-TOKEN=$xsrf"
        headers["X-XSRF-TOKEN"] = xsrf
        return rest.exchange(url(ruta), metodo, HttpEntity<String>(null, headers), String::class.java)
    }

    private fun handshakeXsrf(sesion: String? = null): String {
        val headers = HttpHeaders()
        sesion?.let { headers[HttpHeaders.COOKIE] = "SESSION=$it" }
        val respuesta =
            rest.exchange(
                url("/api/sesion/actual"),
                HttpMethod.GET,
                HttpEntity<String>(null, headers),
                String::class.java,
            )
        val xsrf = cookieValue(respuesta, "XSRF-TOKEN")
        assertNotNull(xsrf, "El handshake debe emitir la cookie XSRF-TOKEN")
        return xsrf!!
    }

    private fun login(usuario: Usuario): String {
        val xsrf = handshakeXsrf()
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers[HttpHeaders.COOKIE] = "XSRF-TOKEN=$xsrf"
        headers["X-XSRF-TOKEN"] = xsrf
        val respuesta =
            rest.exchange(
                url("/api/sesion"),
                HttpMethod.POST,
                HttpEntity("""{"email":"${usuario.email}","password":"$PASSWORD"}""", headers),
                String::class.java,
            )
        respuesta.statusCode shouldBe HttpStatus.OK
        val sesion = cookieValue(respuesta, "SESSION")
        assertNotNull(sesion, "El login debe emitir la cookie SESSION")
        return sesion!!
    }

    private fun url(ruta: String) = "http://localhost:$port$ruta"

    private fun cookieValue(
        respuesta: ResponseEntity<*>,
        nombre: String,
    ): String? =
        respuesta.headers[HttpHeaders.SET_COOKIE]
            ?.firstOrNull { it.startsWith("$nombre=") }
            ?.substringBefore(";")
            ?.substringAfter("=")

    private companion object {
        val CLUB_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
        const val PASSWORD = "motor-authorize-password-12345"
    }
}
