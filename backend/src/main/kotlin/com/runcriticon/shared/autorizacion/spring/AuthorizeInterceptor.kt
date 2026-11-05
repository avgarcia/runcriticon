package com.runcriticon.shared.autorizacion.spring

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.shared.api.events.AccesoDenegado
import com.runcriticon.shared.autorizacion.AuthorizationMatrix
import com.runcriticon.shared.autorizacion.AuthorizeExpression
import com.runcriticon.shared.autorizacion.PrincipalProvider
import com.runcriticon.shared.autorizacion.annotations.Authorize
import com.runcriticon.shared.autorizacion.model.Principal
import com.runcriticon.shared.observability.OpenTelemetryHelper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import java.time.Instant

/**
 * Motor de [Authorize] (ADR-0009 D2): la primera reja RBAC. Antes de que el handler resuelva sus argumentos o llame al
 * caso de uso, comprueba en la [AuthorizationMatrix] que el rol del principal tenga el permiso que declara la
 * expresión. El caso de uso vuelve a comprobarlo (D7): son dos comprobaciones independientes de la misma capa, y esta
 * detecta un caso de uso que consulta un par distinto del que declara su endpoint.
 *
 * - **Deniega fail-closed** si la expresión no existe en la matriz (D2). `AuthorizeExpressionArchTest` impide que una
 *   expresión así llegue a `main`, pero en runtime no se supone.
 * - **Publica [AccesoDenegado]** en cada denegación (D15). No hay transacción del caso de uso a la que unirse, así que
 *   abre la suya: el evento entra en el outbox o la petición falla, nunca hay una denegación sin rastro (D16). Mismo
 *   formato que la denegación RBAC de los casos de uso: `aggregateId` es el propio actor y el motivo, `RBAC`.
 * - **Sin principal** responde como cualquier petición no autenticada (401). Con la `SecurityFilterChain` exigiendo
 *   sesión en toda la API no debería pasar, y sin principal no hay club al que atribuir el evento.
 */
@Component
class AuthorizeInterceptor(
    private val principalProvider: PrincipalProvider,
    private val eventPublisher: ApplicationEventPublisher,
    transactionManager: PlatformTransactionManager,
) : HandlerInterceptor {
    private val log = LoggerFactory.getLogger(javaClass)
    private val transaction = TransactionTemplate(transactionManager)

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        val authorize = (handler as? HandlerMethod)?.getMethodAnnotation(Authorize::class.java) ?: return true
        val principal =
            principalProvider.currentOrNull()
                ?: throw AuthenticationCredentialsNotFoundException("@Authorize sin principal en el contexto")
        val permission = AuthorizeExpression.parse(authorize.expresion)
        if (permission != null && AuthorizationMatrix.can(principal.role, permission.resource, permission.action)) {
            return true
        }
        if (permission == null) {
            log.error("@Authorize con una expresión que no existe en la matriz: {}", authorize.expresion)
        }
        val motivo = if (permission == null) MOTIVO_EXPRESION_DESCONOCIDA else MOTIVO_RBAC
        publishDenial(principal, authorize.expresion, motivo)
        throw AuthorizeDeniedException("${authorize.expresion} denegado al rol ${principal.role}")
    }

    private fun publishDenial(
        principal: Principal,
        recurso: String,
        motivo: String,
    ) {
        transaction.executeWithoutResult {
            eventPublisher.publishEvent(
                AccesoDenegado(
                    eventId = UuidCreator.getTimeOrderedEpoch(),
                    aggregateId = principal.userId,
                    occurredAt = Instant.now(),
                    clubId = principal.clubId,
                    actorId = principal.userId,
                    traceparent = OpenTelemetryHelper.actualTraceparent(),
                    recurso = recurso,
                    motivo = motivo,
                ),
            )
        }
    }

    private companion object {
        const val MOTIVO_RBAC = "RBAC"
        const val MOTIVO_EXPRESION_DESCONOCIDA = "EXPRESION_DESCONOCIDA"
    }
}
