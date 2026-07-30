package com.runcriticon.clubtaxonomia.infrastructure.persistence.projections

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.PersonProjection
import com.runcriticon.clubtaxonomia.domain.person.Person
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Adaptador del puerto [PersonProjection] sobre `JdbcTemplate`.
 *
 * **Por qué SQL plano y no JPA**, a diferencia del resto de la persistencia del módulo: la escritura tiene que ser un
 * upsert condicional atómico. Los listeners del outbox corren en paralelo, así que dos eventos de la misma persona
 * (`AlumnoInvitado` y `AlumnoActivado`) pueden procesarse a la vez; un leer-modificar-escribir con JPA perdería una de
 * las dos escrituras. `INSERT ... ON CONFLICT DO UPDATE ... WHERE` resuelve inserción, actualización y guarda de orden
 * en una sola sentencia, con el bloqueo de fila de PostgreSQL haciendo el trabajo.
 *
 * Tampoco hay `@Entity` para esta tabla: no existiría más que para ser mapeada por esta clase, y no hay todavía ruta
 * de lectura que la aproveche —los listados del club llegarán con las pantallas de gestión de personas—. La categoría
 * RGPD de la tabla (`PII_PRIMARIA`) queda declarada en el comentario de su migración; cuando exista la ruta de lectura
 * y con ella una `@Entity`, `RgpdArchTest` la exigirá también en el código.
 */
@Repository
class PersonProjectionJdbc(
    private val jdbc: JdbcTemplate,
) : PersonProjection {
    /**
     * Escritura dirigida por eventos, no por una petición: corre en el listener del outbox, después del commit del
     * caso de uso que publicó el evento, donde no hay `SecurityContext` ni principal. `@AuthScope(Scope.CLUB)` haría
     * fallar cerrado al aspecto en cada entrega y todo evento acabaría en la DLQ. El `club_id` que se escribe no viene
     * de entrada de usuario: lo trae el propio evento de integración, que lo emitió el módulo dueño del club.
     */
    @NoAuthScope(
        justificacion =
            "Escritura de proyección dirigida por integration events: sin principal en el listener; el club_id " +
                "proviene del evento publicado por identidad, no de entrada de usuario.",
    )
    override fun upsert(
        person: Person,
        eventId: UUID,
        occurredAt: Instant,
    ): Boolean {
        val updated =
            jdbc.update(
                UPSERT_SQL,
                person.id.value,
                person.clubId.value,
                person.name,
                person.email,
                person.role.name,
                person.status.name,
                eventId,
                Timestamp.from(occurredAt),
            )
        return updated == 1
    }

    /**
     * Agregado de sistema sobre la propia proyección: no devuelve datos de ninguna persona ni de ningún club, solo el
     * retraso en segundos que consume el gauge de métricas. No hay nada que filtrar por club, y el llamador es el
     * registro de Micrometer, que tampoco tiene principal.
     */
    @NoAuthScope(
        justificacion =
            "Agregado de sistema para el gauge projection_lag_seconds: no devuelve datos de cliente y lo invoca " +
                "Micrometer, sin principal.",
    )
    override fun lagSeconds(): Long = jdbc.queryForObject(LAG_SQL, Long::class.java) ?: 0L
}

// Las sentencias van a nivel de fichero, no en un `companion object`: leer una propiedad privada del companion desde la
// clase hace que Kotlin genere un accesor sintético *público* (`access$getUPSERT_SQL$cp`), y la malla anti-IDOR de
// ArchUnit lo ve como un método público más del `@Repository` al que le falta su `@AuthScope`.

/**
 * El `WHERE` de la cláusula `DO UPDATE` es la guarda de orden: sin él, el evento que llegue último gana y el estado de
 * la persona puede retroceder a `INVITADO`. Con `>=`, un empate exacto de `occurredAt` lo resuelve el último recibido;
 * reaplicar un mismo evento es inocuo, porque escribe los mismos valores.
 */
private val UPSERT_SQL =
    """
    INSERT INTO club_taxonomia.persona
        (id, club_id, nombre, email, rol, estado, last_processed_event_id, last_processed_event_ts, actualizado_en)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
    ON CONFLICT (id) DO UPDATE SET
        club_id                 = EXCLUDED.club_id,
        nombre                  = EXCLUDED.nombre,
        email                   = EXCLUDED.email,
        rol                     = EXCLUDED.rol,
        estado                  = EXCLUDED.estado,
        last_processed_event_id = EXCLUDED.last_processed_event_id,
        last_processed_event_ts = EXCLUDED.last_processed_event_ts,
        actualizado_en          = now()
    WHERE EXCLUDED.last_processed_event_ts >= club_taxonomia.persona.last_processed_event_ts
    """.trimIndent()

/** `COALESCE` sobre el máximo: una proyección vacía da lag 0, no un lag infinito que dispararía la alarma. */
private val LAG_SQL =
    """
    SELECT EXTRACT(EPOCH FROM (now() - COALESCE(MAX(last_processed_event_ts), now())))::BIGINT
    FROM club_taxonomia.persona
    """.trimIndent()
