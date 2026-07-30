package com.runcriticon.clubtaxonomia.infrastructure.events

import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.events.ProcessedEventTracker
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Implementación de [ProcessedEventTracker] contra `club_taxonomia.evento_procesado`.
 *
 * El contrato vive en `shared` y la implementación aquí, porque la tabla es **de cada módulo**: una implementación
 * compartida tendría que conocer el esquema de todos, que es exactamente el acoplamiento que el esquema por módulo
 * evita. Cada módulo que empiece a consumir eventos añadirá la suya, por eso lleva [Qualifier] desde el primer día —
 * en cuanto exista una segunda, inyectar por tipo sería ambiguo.
 *
 * **Frontera transaccional**: `markIfNew` tiene que ejecutarse en la **misma** transacción que la escritura que
 * protege, y de eso se encarga el `@Transactional` que `@ApplicationModuleListener` ya pone alrededor del listener.
 * Nunca `REQUIRES_NEW` aquí: la marca commitearía por su cuenta, y si después falla la escritura de la proyección, el
 * reintento del outbox descartaría el evento por ya-procesado y el dato se perdería en silencio.
 */
@Repository
@Qualifier(ClubTaxonomiaProcessedEventTracker.QUALIFIER)
class ClubTaxonomiaProcessedEventTracker(
    private val jdbc: JdbcTemplate,
) : ProcessedEventTracker {
    /**
     * Tabla de idempotencia del propio módulo: registra `event_id` ya procesados, sin dato alguno de persona ni
     * `club_id` que filtrar, y se invoca desde el listener del outbox, donde no hay principal.
     */
    @NoAuthScope(
        justificacion =
            "Tabla de idempotencia interna del módulo (SIN_PII): no contiene datos de cliente ni club_id, y se " +
                "invoca desde un listener del outbox, sin principal.",
    )
    override fun markIfNew(
        listener: String,
        eventId: UUID,
    ): Boolean = jdbc.update(MARK_SQL, listener, eventId) == 1

    companion object {
        /** Nombre del qualifier con el que los listeners de este módulo piden su tracker. */
        const val QUALIFIER = "clubTaxonomiaProcessedEventTracker"
    }
}

/**
 * `DO NOTHING` deja el `update` en 0 filas cuando el par (listener, evento) ya estaba: evento ya procesado.
 *
 * A nivel de fichero y no en el `companion object`: una propiedad privada del companion leída desde la clase genera un
 * accesor sintético público, que la malla anti-IDOR de ArchUnit contaría como un método más del `@Repository` al que le
 * falta su `@AuthScope`.
 */
private val MARK_SQL =
    """
    INSERT INTO club_taxonomia.evento_procesado (listener, event_id)
    VALUES (?, ?)
    ON CONFLICT (listener, event_id) DO NOTHING
    """.trimIndent()
