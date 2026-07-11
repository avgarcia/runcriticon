package com.runcriticon.shared.observability

import com.runcriticon.shared.events.IntegrationEvent
import org.slf4j.MDC
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Restaura en el MDC el contexto operativo de un evento consumido por un `@ApplicationModuleListener`
 * (ADR-0011 D4/D5, observabilidad-por-modulo): sin esto, los logs de un listener no llevan `trace_id`
 * ni `club_id` ni `user_id_hash` — se pierde la correlación con la petición que originó el evento.
 *
 * `@Component` (no `object`): necesita [UserIdHasher] inyectado para no emitir nunca el `userId` en
 * claro (ADR-0014 D9). Uso obligatorio en cada listener: `restore(...)` al principio, `clear()` en el
 * `finally` — igual que un `try`/`finally` de recursos.
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

    /**
     * `com.runcriticon.identidad.application.ports.InvitationEmailRequested` → `identidad`.
     *
     * El tag `module` debe coincidir con el nombre del esquema SQL del módulo (ADR-0011 D9,
     * ADR-0004 D4), no con el paquete Kotlin: `club_taxonomia` lleva guion bajo en el esquema pero
     * no en el paquete raíz (`clubtaxonomia`, ADR-0008 D4 — paquetes raíz de bounded context van sin
     * guion bajo). [SCHEMA_BY_PACKAGE] traduce los paquetes que divergen de su esquema; el resto pasa
     * tal cual porque coincide por casualidad (`identidad`, `planificacion`, `seguimiento`, `auditoria`).
     */
    private fun moduleOf(event: Any): String {
        val packageName =
            event::class.java.packageName
                .removePrefix("com.runcriticon.")
                .substringBefore(".")
        return SCHEMA_BY_PACKAGE[packageName] ?: packageName
    }

    private companion object {
        const val W3C_TRACEPARENT_PART_COUNT = 4
        const val TRACE_ID_PART_INDEX = 1
        const val TRACE_ID_HEX_LENGTH = 32
        val SCHEMA_BY_PACKAGE = mapOf("clubtaxonomia" to "club_taxonomia")
    }
}
