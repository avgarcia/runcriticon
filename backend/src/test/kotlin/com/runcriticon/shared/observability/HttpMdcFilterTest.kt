package com.runcriticon.shared.observability

import com.runcriticon.clubtaxonomia.FakeClubTaxonomiaHandler
import com.runcriticon.identidad.infrastructure.rest.FakeIdentidadHandler
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.slf4j.MDC
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerExecutionChain
import org.springframework.web.servlet.HandlerMapping
import java.util.UUID
import kotlin.reflect.jvm.javaMethod

/**
 * Unitario de [HttpMdcFilter] (ADR-0011 D5): sin contexto Spring, con mocks de servlet y de
 * [HandlerMapping] (para no depender de que Spring MVC ya haya resuelto la ruta real).
 *
 * El `FilterChain` capta una foto del MDC **dentro** de la cadena: el propio filtro lo limpia en
 * su `finally` antes de que `doFilter(...)` devuelva el control al test.
 */
class HttpMdcFilterTest :
    FunSpec({
        val userIdHasher = mockk<UserIdHasher>()
        every { userIdHasher.hash(any()) } returns "hash-del-actor"
        val mdcRestorer = MdcRestorerForEvents(userIdHasher)
        val principalProvider = mockk<PrincipalProvider>()

        fun handlerMappingFor(handler: Any?): HandlerMapping =
            mockk<HandlerMapping> {
                every { getHandler(any()) } returns handler?.let { HandlerExecutionChain(it) }
            }

        fun runFilter(
            handlerMappings: List<HandlerMapping>,
            traceparent: String? = null,
            activeProfile: String? = "staging",
        ): Map<String, String?> {
            val environment = MockEnvironment()
            activeProfile?.let { environment.addActiveProfile(it) }
            val filter = HttpMdcFilter(mdcRestorer, principalProvider, handlerMappings, environment)
            val request = MockHttpServletRequest()
            traceparent?.let { request.addHeader("traceparent", it) }

            var snapshot: Map<String, String?> = emptyMap()
            filter.doFilter(request, MockHttpServletResponse()) { _, _ ->
                snapshot =
                    mapOf(
                        "module" to MDC.get("module"),
                        "trace_id" to MDC.get("trace_id"),
                        "club_id" to MDC.get("club_id"),
                        "user_id_hash" to MDC.get("user_id_hash"),
                        "env" to MDC.get("env"),
                    )
            }
            return snapshot
        }

        afterEach { MDC.clear() }

        test("module se deriva del controller resuelto por Spring MVC (identidad)") {
            val method = HandlerMethod(FakeIdentidadHandler(), FakeIdentidadHandler::handle.javaMethod!!)
            every { principalProvider.current() } throws IllegalStateException("sin autenticacion")

            val mdc = runFilter(listOf(handlerMappingFor(method)))

            mdc["module"] shouldBe "identidad"
        }

        test("module traduce clubtaxonomia (paquete) a club_taxonomia (esquema, ADR-0011 D9)") {
            val method = HandlerMethod(FakeClubTaxonomiaHandler(), FakeClubTaxonomiaHandler::handle.javaMethod!!)
            every { principalProvider.current() } throws IllegalStateException("sin autenticacion")

            val mdc = runFilter(listOf(handlerMappingFor(method)))

            mdc["module"] shouldBe "club_taxonomia"
        }

        test("sin ruta resuelta (404): module es unmatched, no lanza") {
            every { principalProvider.current() } throws IllegalStateException("sin autenticacion")

            val mdc = runFilter(listOf(handlerMappingFor(null)))

            mdc["module"] shouldBe "unmatched"
        }

        test("peticion anonima: user_id_hash cae a system, sin club_id") {
            val method = HandlerMethod(FakeIdentidadHandler(), FakeIdentidadHandler::handle.javaMethod!!)
            every { principalProvider.current() } throws IllegalStateException("sin autenticacion")

            val mdc = runFilter(listOf(handlerMappingFor(method)))

            mdc["user_id_hash"] shouldBe "system"
            mdc["club_id"].shouldBeNull()
        }

        test("peticion autenticada: club_id y user_id_hash del principal") {
            val clubId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val method = HandlerMethod(FakeIdentidadHandler(), FakeIdentidadHandler::handle.javaMethod!!)
            every { principalProvider.current() } returns Principal(userId = userId, clubId = clubId, role = Role.ADMIN)

            val mdc = runFilter(listOf(handlerMappingFor(method)))

            mdc["club_id"] shouldBe clubId.toString()
            mdc["user_id_hash"] shouldBe "hash-del-actor"
        }

        test("traceparent W3C valido: trace_id se rellena") {
            val method = HandlerMethod(FakeIdentidadHandler(), FakeIdentidadHandler::handle.javaMethod!!)
            every { principalProvider.current() } throws IllegalStateException("sin autenticacion")

            val mdc =
                runFilter(
                    listOf(handlerMappingFor(method)),
                    traceparent = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01",
                )

            mdc["trace_id"] shouldBe "0af7651916cd43dd8448eb211c80319c"
        }

        test("env del primer perfil Spring activo") {
            val method = HandlerMethod(FakeIdentidadHandler(), FakeIdentidadHandler::handle.javaMethod!!)
            every { principalProvider.current() } throws IllegalStateException("sin autenticacion")

            val mdc = runFilter(listOf(handlerMappingFor(method)), activeProfile = "production")

            mdc["env"] shouldBe "production"
        }

        test("el filtro limpia el MDC en su finally: no sobrevive a la peticion") {
            val method = HandlerMethod(FakeIdentidadHandler(), FakeIdentidadHandler::handle.javaMethod!!)
            every { principalProvider.current() } throws IllegalStateException("sin autenticacion")

            runFilter(listOf(handlerMappingFor(method)))

            MDC.get("module").shouldBeNull()
        }
    })
