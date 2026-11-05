package com.runcriticon.shared.autorizacion.spring

import com.runcriticon.shared.api.events.AccesoDenegado
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.Authorize
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.autorizacion.model.Role
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.springframework.context.ApplicationEventPublisher
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.method.HandlerMethod
import java.util.UUID

class AuthorizeInterceptorTest :
    FunSpec({
        val principalProvider = mockk<PrincipalProvider>()
        val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
        val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)
        val interceptor = AuthorizeInterceptor(principalProvider, eventPublisher, transactionManager)

        val clubId = UUID.randomUUID()
        val admin = Principal(userId = UUID.randomUUID(), clubId = clubId, role = Role.ADMIN)
        val entrenador = Principal(userId = UUID.randomUUID(), clubId = clubId, role = Role.ENTRENADOR)

        fun handler(name: String) = HandlerMethod(FakeController(), FakeController::class.java.getMethod(name))

        fun preHandle(handler: Any) =
            interceptor.preHandle(MockHttpServletRequest(), MockHttpServletResponse(), handler)

        beforeEach { clearMocks(principalProvider, eventPublisher, transactionManager, answers = false) }

        test("deja pasar al rol que tiene el permiso, sin publicar nada") {
            every { principalProvider.currentOrNull() } returns admin

            preHandle(handler("deleteUser")) shouldBe true

            verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
        }

        test("deniega al rol sin el permiso y publica AccesoDenegado dentro de una transaccion") {
            every { principalProvider.currentOrNull() } returns entrenador
            val evento = slot<Any>()
            every { eventPublisher.publishEvent(capture(evento)) } returns Unit

            shouldThrow<AuthorizeDeniedException> { preHandle(handler("deleteUser")) }

            val denegado = evento.captured as AccesoDenegado
            denegado.recurso shouldBe "USER:DELETE"
            denegado.motivo shouldBe "RBAC"
            denegado.actorId shouldBe entrenador.userId
            denegado.aggregateId shouldBe entrenador.userId
            denegado.clubId shouldBe clubId
            denegado.sujetoId shouldBe null
            verifyOrder {
                transactionManager.getTransaction(any())
                eventPublisher.publishEvent(any<Any>())
                transactionManager.commit(any())
            }
        }

        test("una expresion que no existe en la matriz deniega incluso al admin") {
            every { principalProvider.currentOrNull() } returns admin
            val evento = slot<Any>()
            every { eventPublisher.publishEvent(capture(evento)) } returns Unit

            shouldThrow<AuthorizeDeniedException> { preHandle(handler("unknownPermission")) }

            (evento.captured as AccesoDenegado).motivo shouldBe "EXPRESION_DESCONOCIDA"
        }

        test("sin principal responde como peticion no autenticada y no publica nada") {
            every { principalProvider.currentOrNull() } returns null

            shouldThrow<AuthenticationCredentialsNotFoundException> { preHandle(handler("deleteUser")) }

            verify(exactly = 0) { eventPublisher.publishEvent(any<Any>()) }
        }

        test("un handler sin @Authorize pasa sin consultar el principal") {
            preHandle(handler("withoutAuthorize")) shouldBe true

            verify(exactly = 0) { principalProvider.currentOrNull() }
        }

        test("lo que no es un handler de controlador, como un recurso estatico, pasa sin tocar") {
            preHandle(Any()) shouldBe true

            verify(exactly = 0) { principalProvider.currentOrNull() }
        }
    })

@Suppress("FunctionOnlyReturningConstant")
private class FakeController {
    @Authorize("USER:DELETE")
    fun deleteUser() = "ok"

    @Authorize("NO_EXISTE:NADA")
    fun unknownPermission() = "ok"

    fun withoutAuthorize() = "ok"
}
