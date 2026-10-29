package com.runcriticon.testing

import com.runcriticon.shared.events.ProcessedEventTracker
import org.slf4j.MDC
import java.util.UUID

/**
 * Doble de [ProcessedEventTracker] con la misma semántica que la clave primaria `(listener, event_id)` de
 * `evento_procesado`: para los tests unitarios de listeners, que prueban la idempotencia sin base de datos.
 *
 * Todo listener llama a [markIfNew] justo después de restaurar el MDC y dentro de su `try`, así que el doble apunta el
 * MDC vigente en ese momento ([mdcAtMark]). Para probar que el `finally` lo limpia, puede fallar a voluntad
 * ([failure]) o dar todo evento por ya procesado ([treatAllAsProcessed]), que hace salir al listener antes de tocar
 * ningún puerto.
 */
class InMemoryProcessedEventTracker : ProcessedEventTracker {
    private val processed = mutableSetOf<Pair<String, UUID>>()
    val mdcAtMark = mutableMapOf<String, String>()
    var failure: RuntimeException? = null
    var treatAllAsProcessed = false

    override fun markIfNew(
        listener: String,
        eventId: UUID,
    ): Boolean {
        MDC.getCopyOfContextMap()?.let(mdcAtMark::putAll)
        failure?.let { throw it }
        if (treatAllAsProcessed) return false
        return processed.add(listener to eventId)
    }

    fun contains(eventId: UUID): Boolean = processed.any { it.second == eventId }
}
