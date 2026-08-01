package com.runcriticon.clubtaxonomia.infrastructure.persistence.projections

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.ErasedRows
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.PersonErasure
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * Adaptador del puerto [PersonErasure] sobre `JdbcTemplate`, en la línea de [PersonProjectionJdbc]: SQL plano, sin
 * `@Entity`. Para `alumno_tag` ni siquiera existe modelo —esa tabla no tiene agregado ni ruta de lectura todavía—, y
 * crear una entidad solo para borrar arrastraría mapeador y anotaciones por una tabla que nadie lee.
 *
 * **Sin transacción propia**, ni mucho menos `REQUIRES_NEW`: la lápida, los dos borrados y la marca de idempotencia del
 * listener tienen que caer juntos o ninguno. Cabalga la transacción que abre `@ApplicationModuleListener`.
 *
 * El orden importa: la lápida se escribe **antes** de borrar, para que una escritura concurrente que estuviera
 * esperando el lock encuentre ya la marca y se descarte a sí misma.
 */
@Repository
class PersonErasureJdbc(
    private val jdbc: JdbcTemplate,
) : PersonErasure {
    /**
     * Borrado dirigido por eventos, sin `SecurityContext` ni principal: un `@AuthScope(Scope.CLUB)` haría fallar
     * cerrado al aspecto en cada entrega y las supresiones acabarían en la DLQ, que es el peor sitio donde puede
     * acabar un derecho de supresión.
     */
    @NoAuthScope(
        justificacion =
            "Borrado RGPD dirigido por integration events: sin principal en el listener; el sujeto lo identifica el " +
                "evento publicado por identidad, no entrada de usuario.",
    )
    override fun erase(personId: PersonId): ErasedRows {
        lockPerson(jdbc, personId.value)
        jdbc.update(TOMBSTONE_SQL, personId.value)
        val projections = jdbc.update(DELETE_PERSON_SQL, personId.value)
        val tagAssignments = jdbc.update(DELETE_TAGS_SQL, personId.value)
        return ErasedRows(projections = projections, tagAssignments = tagAssignments)
    }
}

// SQL a nivel de fichero, no en un `companion object`: una propiedad privada del companion leída desde la clase genera
// un accesor sintético público que la malla anti-IDOR contaría como método del `@Repository` sin `@AuthScope`.

/** `DO NOTHING` hace idempotente la lápida: repetir el borrado no falla ni mueve la fecha de la primera supresión. */
private val TOMBSTONE_SQL =
    """
    INSERT INTO club_taxonomia.persona_eliminada (id)
    VALUES (?)
    ON CONFLICT (id) DO NOTHING
    """.trimIndent()

// Los dos borrados van por el id de la persona **sin** filtrar por club, y es deliberado: la clave primaria ya lo
// identifica unívocamente, y añadir un `club_id` que no cuadrara convertiría el borrado en un no-borrado silencioso.
// En una ruta de supresión, no borrar sin que nadie se entere es peor que fallar ruidosamente.
private const val DELETE_PERSON_SQL = "DELETE FROM club_taxonomia.persona WHERE id = ?"

private const val DELETE_TAGS_SQL = "DELETE FROM club_taxonomia.alumno_tag WHERE alumno_id = ?"
