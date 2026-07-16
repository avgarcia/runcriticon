package com.runcriticon.shared.observability

import com.runcriticon.shared.events.IntegrationEvent
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Restaura en el MDC el contexto operativo de un evento consumido por un `@ApplicationModuleListener`: sin esto, los
 * logs de un listener no llevan `trace_id` ni `club_id` ni `user_id_hash` — se pierde la correlación con la petición
 * que originó el evento.
 *
 * `@Component` (no `object`): necesita [UserIdHasher] inyectado para no emitir nunca el `userId` en claro. Uso
 * obligatorio en cada listener: `restore(...)` al principio, `clear()` en el `finally` — igual que un `try`/`finally`
 * de recursos.
 */
@Component
class MdcRestorerForEvents(
    private val userIdHasher: UserIdHasher,
) {
    /** Variante para [IntegrationEvent]: el módulo se deriva del paquete de la clase del evento. */
    fun restore(event: IntegrationEvent) =
        restore(
            module = moduleOf(event),
            traceparent = event.traceparent,
            clubId = event.clubId,
            actorId = event.actorId,
        )

    /** Variante para eventos internos de aplicación que no implementan [IntegrationEvent]. */
    fun restore(
        module: String,
        traceparent: String?,
        clubId: UUID?,
        actorId: UUID?,
    ) {
        traceIdOf(traceparent)?.let { MDC.put("trace_id", it) }
        clubId?.let { MDC.put("club_id", it.toString()) }
        MDC.put("user_id_hash", actorId?.let(userIdHasher::hash) ?: "system")
        MDC.put("module", module)
    }

    fun clear() = MDC.clear()

    /** Extrae el trace-id de un `traceparent` W3C (`00-<32 hex>-<16 hex>-<2 hex>`); `null` si es inválido. */
    private fun traceIdOf(traceparent: String?): String? {
        val parts = traceparent?.split("-") ?: return null
        return parts
            .getOrNull(TRACE_ID_PART_INDEX)
            ?.takeIf { parts.size == W3C_TRACEPARENT_PART_COUNT && it.length == TRACE_ID_HEX_LENGTH }
    }

    /** `com.runcriticon.identidad.application.ports.InvitationEmailRequested` → `identidad`. */
    private fun moduleOf(event: Any): String {
        val rootPackage =
            event::class.java.packageName
                .removePrefix("com.runcriticon.")
                .substringBefore(".")
        return ModuleTagResolver.resolve(rootPackage)
    }

    private companion object {
        const val W3C_TRACEPARENT_PART_COUNT = 4
        const val TRACE_ID_PART_INDEX = 1
        const val TRACE_ID_HEX_LENGTH = 32
    }
}
