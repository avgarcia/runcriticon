package com.runcriticon.testing

import org.springframework.jdbc.core.JdbcTemplate
import java.time.Duration
import java.util.UUID
import kotlin.reflect.KClass

/**
 * Espera a que el outbox termine de entregar un evento a un listener concreto, leyendo `event_publication`.
 *
 * Sustituye al `Thread.sleep` de los tests que afirman que algo **no** cambia (una reentrega descartada, un evento
 * fuera de orden, un borrado sin filas): sin esperar a la entrega, esa aserción pasa también cuando el listener
 * ni siquiera ha corrido. Filtra por el prefijo de la clase del listener, como `PendingDeliveryLagJdbc`, porque un
 * mismo evento lo consumen varios listeners (un `AlumnoEliminado` llega a cuatro módulos).
 */
class OutboxDeliveries(
    private val jdbc: JdbcTemplate,
) {
    /** Espera a que [listener] tenga [times] entregas completadas del evento [eventId]. */
    fun awaitCompleted(
        listener: KClass<*>,
        eventId: UUID,
        times: Int = 1,
    ) {
        val deadlineNanos = System.nanoTime() + DEADLINE.toNanos()
        while (System.nanoTime() < deadlineNanos) {
            if (completed(listener, eventId) >= times) return
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError(
            "${listener.simpleName} completó ${completed(listener, eventId)} de $times entregas de $eventId " +
                "en ${DEADLINE.seconds} s",
        )
    }

    /** Entregas completadas de [eventId] a [listener]. */
    fun completed(
        listener: KClass<*>,
        eventId: UUID,
    ): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM event_publication " +
                "WHERE starts_with(listener_id, ?) AND serialized_event LIKE ? AND completion_date IS NOT NULL",
            Int::class.java,
            "${listener.java.name}.",
            "%$eventId%",
        ) ?: 0

    private companion object {
        val DEADLINE: Duration = Duration.ofSeconds(5)
        const val POLL_MILLIS = 25L
    }
}
