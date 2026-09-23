package com.runcriticon.identidad.infrastructure.rest

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.infrastructure.persistence.entities.ClubEntity
import com.runcriticon.identidad.infrastructure.persistence.entities.UserEntity
import com.runcriticon.identidad.infrastructure.persistence.repositories.ClubEntityRepository
import com.runcriticon.identidad.infrastructure.persistence.repositories.UserEntityRepository
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.testing.IntegrationTestBase
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
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
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.util.UUID

/**
 * `GET /api/me/permissions` con sesión HTTP real (P0-9 de la auditoría de testing 2026-09): hasta ahora solo se
 * cubría con el caso de uso mockeado ([MeControllerTest]). Verifica que el body que ve el frontend coincide con
 * [AuthorizationMatrix.grantedTo] para cada rol, y que sin sesión el endpoint devuelve 401 (ADR-0009 D18: ayuda
 * de UX, nunca barrera — pero sigue exigiendo sesión, vía `@AuthenticatedOnly`).
 */
class MePermissionsIntegrationTest : IntegrationTestBase() {
    @LocalServerPort
    private var port: Int = 0

    private object LaxErrorHandler : DefaultResponseErrorHandler() {
        override fun hasError(response: ClientHttpResponse) = false
    }

    private val rest = RestTemplate().apply { errorHandler = LaxErrorHandler }
    private val objectMapper = ObjectMapper()

    @Autowired
    lateinit var users: UserEntityRepository

    @Autowired
    lateinit var clubs: ClubEntityRepository

    @Autowired
    lateinit var encoder: PasswordEncoder

    private val clubId: UUID = UuidCreator.getTimeOrderedEpoch()

    @BeforeEach
    fun seedClub() {
        val now = Instant.now()
        clubs.save(ClubEntity(id = clubId, name = "Club de prueba", slug = null, createdAt = now, modifiedAt = now))
    }

    private fun seed(
        role: Role,
        email: String,
    ) {
        val now = Instant.now()
        users.save(
            UserEntity(
                id = UuidCreator.getTimeOrderedEpoch(),
                clubId = clubId,
                email = email,
                normalizedEmail = email,
                name = "Test $role",
                role = role.name,
                passwordHash = encoder.encode(PASSWORD),
                status = "ACTIVO",
                createdAt = now,
                modifiedAt = now,
            ),
        )
    }

    @Test
    fun `permissions por HTTP real coincide con AuthorizationMatrix para ADMIN, ENTRENADOR y ALUMNO`() {
        Role.entries.forEach { role ->
            val cookies = mutableMapOf<String, String>()
            val email = "${role.name.lowercase()}@me-permissions-test.local"
            seed(role, email)

            get("/api/sesion/actual", cookies)
            val login = postJson("/api/sesion", """{"email":"$email","password":"$PASSWORD"}""", cookies)
            login.statusCode shouldBe HttpStatus.OK

            val response = get("/api/me/permissions", cookies)
            response.statusCode shouldBe HttpStatus.OK

            val body: Map<String, List<String>> =
                objectMapper.readValue(response.body, object : TypeReference<Map<String, List<String>>>() {})
            val expected =
                AuthorizationMatrix
                    .grantedTo(role)
                    .mapKeys { (resource, _) -> resource.name }
                    .mapValues { (_, actions) -> actions.map { it.name }.toSet() }

            body.mapValues { (_, actions) -> actions.toSet() } shouldBe expected
        }
    }

    @Test
    fun `permissions sin sesion devuelve 401`() {
        val response = get("/api/me/permissions", mutableMapOf())

        response.statusCode shouldBe HttpStatus.UNAUTHORIZED
    }

    private fun get(
        path: String,
        cookies: MutableMap<String, String>,
    ): ResponseEntity<String> = exchange(path, HttpMethod.GET, null, cookies)

    private fun postJson(
        path: String,
        body: String,
        cookies: MutableMap<String, String>,
    ): ResponseEntity<String> = exchange(path, HttpMethod.POST, body, cookies)

    private fun exchange(
        path: String,
        method: HttpMethod,
        body: String?,
        cookies: MutableMap<String, String>,
    ): ResponseEntity<String> {
        val headers = HttpHeaders()
        if (cookies.isNotEmpty()) {
            headers[HttpHeaders.COOKIE] = cookies.entries.joinToString("; ") { (name, value) -> "$name=$value" }
        }
        if (method != HttpMethod.GET) {
            headers.contentType = MediaType.APPLICATION_JSON
            cookies["XSRF-TOKEN"]?.let { headers["X-XSRF-TOKEN"] = it }
        }
        val response =
            rest.exchange(
                "http://localhost:$port$path",
                method,
                HttpEntity(body, headers),
                String::class.java,
            )
        response.headers[HttpHeaders.SET_COOKIE]?.forEach { setCookie ->
            val pair = setCookie.substringBefore(";")
            val name = pair.substringBefore("=")
            val value = pair.substringAfter("=")
            if (value.isBlank()) cookies.remove(name) else cookies[name] = value
        }
        return response
    }

    companion object {
        private const val PASSWORD = "me-permissions-test-12345"
    }
}
