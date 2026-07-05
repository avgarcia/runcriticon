package com.runcriticon.shared.autorizacion.spring

import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import com.runcriticon.testing.MutableClock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Unitario del tope absoluto de sesión (ADR-0003 D10, LAL-57): sin contexto Spring, con mocks de
 * servlet y reloj controlado. El tope por defecto es el real (90 días).
 */
class AbsoluteSessionTimeoutFilterTest :
    FunSpec({
        val now = Instant.parse("2026-07-05T10:00:00Z")
        val clock = MutableClock(now)
        val sessionManager = mockk<SecuritySessionManager>(relaxed = true)
        val filter = AbsoluteSessionTimeoutFilter(SecuritySessionProperties(), sessionManager, clock)

        fun authenticate(authenticatedAt: Instant?) {
            val principal = Principal(userId = UUID.randomUUID(), clubId = UUID.randomUUID(), role = Role.ADMIN)
            val authentication =
                UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_ADMIN")))
            authenticatedAt?.let { authentication.details = SessionAuthenticationDetails(authenticatedAt = it) }
            val context = SecurityContextHolder.createEmptyContext()
            context.authentication = authentication
            SecurityContextHolder.setContext(context)
        }

        fun runFilter(): Pair<MockHttpServletResponse, MockFilterChain> {
            val response = MockHttpServletResponse()
            val chain = MockFilterChain()
            filter.doFilter(MockHttpServletRequest(), response, chain)
            return response to chain
        }

        beforeTest {
            SecurityContextHolder.clearContext()
            clearMocks(sessionManager)
        }

        afterSpec { SecurityContextHolder.clearContext() }

        test("sin principal autenticado la cadena continúa") {
            val (response, chain) = runFilter()

            chain.request.shouldNotBeNull()
            response.status shouldBe HttpStatus.OK.value()
            verify(exactly = 0) { sessionManager.endSession(any(), any()) }
        }

        test("sesión dentro del tope: la cadena continúa") {
            authenticate(authenticatedAt = now.minus(Duration.ofDays(89)))

            val (response, chain) = runFilter()

            chain.request.shouldNotBeNull()
            response.status shouldBe HttpStatus.OK.value()
            verify(exactly = 0) { sessionManager.endSession(any(), any()) }
        }

        test("sesión que supera los 90 días: se invalida y responde 401") {
            authenticate(authenticatedAt = now.minus(Duration.ofDays(91)))

            val (response, chain) = runFilter()

            chain.request.shouldBeNull()
            response.status shouldBe HttpStatus.UNAUTHORIZED.value()
            verify(exactly = 1) { sessionManager.endSession(any(), any()) }
        }

        test("sesión autenticada sin marca de autenticación: 401 fail-closed") {
            authenticate(authenticatedAt = null)

            val (response, chain) = runFilter()

            chain.request.shouldBeNull()
            response.status shouldBe HttpStatus.UNAUTHORIZED.value()
            verify(exactly = 1) { sessionManager.endSession(any(), any()) }
        }
    })
