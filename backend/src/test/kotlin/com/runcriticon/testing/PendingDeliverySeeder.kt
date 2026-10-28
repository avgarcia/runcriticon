package com.runcriticon.testing

import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Siembra filas crudas en `event_publication` como las dejaría el outbox, para probar los gauges
 * `projection_lag_seconds` sin esperar a que una entrega real se atasque.
 *
 * `event_publication` la comparte toda la JVM de tests: cada fila lleva [marker] en el payload y [cleanUp] borra solo
 * esas. Cada payload lleva además un `aggregateId` aleatorio, para no tocar la puerta de publicación de
 * `planificacion`, que filtra por grupo. El payload no es un evento deserializable: estas filas no deben llegar a
 * reentregarse, de ahí que haya que limpiarlas al terminar cada test.
 */
class PendingDeliverySeeder(
    private val jdbc: JdbcTemplate,
    private val marker: String,
) {
    /**
     * Entrega a [listener] del evento [event] publicada hace [age]. Sin [completed] queda pendiente, con el [status]
     * dado (`PUBLISHED` o `FAILED`); con [completed], completada un segundo después de publicarse.
     */
    fun seed(
        listener: KClass<*>,
        event: KClass<*>,
        age: Duration,
        status: String = "PUBLISHED",
        completed: Boolean = false,
    ) = seed("${listener.java.name}.on(${event.java.name})", event.java.name, age, status, completed)

    /** Igual que la otra variante, con el `listener_id` literal: para listeners que no existen como clase. */
    fun seed(
        listenerId: String,
        eventType: String,
        age: Duration,
        status: String = "PUBLISHED",
        completed: Boolean = false,
    ) {
        val publishedAt = Instant.now().minus(age)
        jdbc.update(
            """
            INSERT INTO event_publication
                (id, listener_id, event_type, serialized_event, publication_date, completion_date, status)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            listenerId,
            eventType,
            """{"$marker":true,"aggregateId":"${UUID.randomUUID()}"}""",
            Timestamp.from(publishedAt),
            if (completed) Timestamp.from(publishedAt.plusSeconds(1)) else null,
            if (completed) "COMPLETED" else status,
        )
    }

    fun cleanUp() {
        jdbc.update("DELETE FROM event_publication WHERE serialized_event LIKE ?", "%\"$marker\"%")
    }
}
