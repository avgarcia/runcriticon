package com.runcriticon.shared.events.infrastructure

import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.events.PendingDeliveryLag
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass

/**
 * Adaptador de [PendingDeliveryLag] sobre `event_publication`, la tabla compartida del outbox de Spring Modulith (sin
 * `club_id`, de ahí [NoAuthScope]).
 *
 * Spring Modulith graba el `listener_id` de un `@ApplicationModuleListener` como `<clase>.<método>(<evento>)`, así que
 * el filtro es un prefijo por clase: `<nombre completo de la clase>.`. El punto final evita casar con otra clase cuyo
 * nombre empiece igual, y no se da por hecho que el método se llame `on`. Si un listener declarara un `id` propio en
 * la anotación, su `listener_id` no llevaría este prefijo y el gauge lo ignoraría: hoy ninguno lo hace.
 * `PublishPlanIntegrationTest` verifica el formato contra una entrega real.
 *
 * La antigüedad se calcula con el reloj de la aplicación, no con `now()` de Postgres, porque `publication_date` también
 * lo escribe la aplicación.
 */
@Repository
class PendingDeliveryLagJdbc(
    private val jdbc: JdbcTemplate,
) : PendingDeliveryLag {
    @NoAuthScope(
        justificacion =
            "Agregado de sistema para los gauges projection_lag_seconds sobre event_publication, la tabla compartida " +
                "del outbox: no tiene club_id, no devuelve datos de cliente y lo invoca Micrometer, sin principal.",
    )
    override fun oldestPendingSeconds(listeners: Set<KClass<*>>): Long {
        require(listeners.isNotEmpty()) { "Sin listeners se mediría el outbox entero" }
        val prefixes = listeners.map { "${it.java.name}." }
        val sql = OLDEST_PENDING_SQL + prefixes.joinToString(" OR ", prefix = "(", postfix = ")") { PREFIX_CLAUSE }
        return jdbc
            .queryForObject(sql, Timestamp::class.java, *prefixes.toTypedArray())
            ?.let { Duration.between(it.toInstant(), Instant.now()).seconds }
            ?: 0L
    }
}

// Las sentencias van a nivel de fichero, no en un `companion object`: leer una propiedad privada del companion desde la
// clase genera un accesor sintético público, y la malla anti-IDOR de ArchUnit lo ve como un método público más del
// `@Repository` sin `@AuthScope`.

/**
 * Sin filtro de `status`: una `FAILED` sigue pendiente. `starts_with` y no `LIKE`, porque en un patrón `LIKE` el `_` es
 * comodín. Solo se concatena [PREFIX_CLAUSE], una constante; los prefijos van como parámetros enlazados.
 */
private const val OLDEST_PENDING_SQL =
    "SELECT MIN(publication_date) FROM event_publication WHERE completion_date IS NULL AND "

private const val PREFIX_CLAUSE = "starts_with(listener_id, ?)"
