package com.runcriticon.clubtaxonomia.infrastructure.persistence.repositories

import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.StudentTagRepository
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.PreparedStatement
import java.util.UUID

/**
 * Adaptador de [StudentTagRepository] sobre `JdbcTemplate`.
 *
 * **Sin `@Entity`, y no por gusto**: la supresión de una persona ya borra `alumno_tag` con SQL plano desde el listener
 * de bajas. Introducir una entidad JPA sobre la misma tabla haría convivir el contexto de persistencia de Hibernate
 * con ese borrado sin que se conozcan, y Hibernate podría servir de su caché filas que el borrado acaba de eliminar.
 * Una sola vía de acceso por tabla. La categoría RGPD de `alumno_tag` (`PII_PRIMARIA`) está declarada en el comentario
 * de su migración.
 *
 * Todas las sentencias filtran por `club_id` además de por la clave primaria. Es defensa en profundidad: los casos de
 * uso ya validan que el alumno y los valores son del club del principal, pero un fallo ahí no debe convertirse en una
 * escritura o un borrado en datos ajenos.
 */
@Repository
class StudentTagRepositoryJdbc(
    private val jdbc: JdbcTemplate,
) : StudentTagRepository {
    @AuthScope(Scope.CLUB)
    override fun findAssignedValueIds(
        clubId: ClubId,
        studentId: PersonId,
    ): Set<TagValueId> =
        jdbc
            .queryForList(FIND_SQL, UUID::class.java, clubId.value, studentId.value)
            // La columna es NOT NULL; el filtro solo satisface al tipo de plataforma que devuelve el driver.
            .filterNotNull()
            .mapTo(mutableSetOf()) { TagValueId.of(it) }

    /**
     * Dos sentencias, no un borrado seguido de una inserción completa: así las filas que sobreviven conservan su
     * `creado_en`. La carrera con otro reemplazo simultáneo del mismo alumno la cierra el bloqueo que el caso de uso
     * ya tomó al comprobar que el alumno existe.
     */
    @AuthScope(Scope.CLUB)
    override fun replace(
        clubId: ClubId,
        studentId: PersonId,
        valueIds: Set<TagValueId>,
    ) {
        val keep = valueIds.map { it.value }.toTypedArray()
        jdbc.update(DELETE_MISSING_SQL) { statement: PreparedStatement ->
            statement.setObject(CLUB_PARAM, clubId.value)
            statement.setObject(STUDENT_PARAM, studentId.value)
            statement.setArray(KEEP_PARAM, statement.connection.createArrayOf("uuid", keep))
        }
        if (valueIds.isEmpty()) return

        jdbc.batchUpdate(
            INSERT_SQL,
            valueIds.map { arrayOf<Any>(clubId.value, studentId.value, it.value) },
        )
    }

    @AuthScope(Scope.CLUB)
    override fun add(
        clubId: ClubId,
        studentId: PersonId,
        valueId: TagValueId,
    ) {
        jdbc.update(INSERT_SQL, clubId.value, studentId.value, valueId.value)
    }

    @AuthScope(Scope.CLUB)
    override fun remove(
        clubId: ClubId,
        studentId: PersonId,
        valueId: TagValueId,
    ) {
        jdbc.update(DELETE_ONE_SQL, clubId.value, studentId.value, valueId.value)
    }
}

// SQL a nivel de fichero: en un `companion object` generaría accesores sintéticos públicos que la malla anti-IDOR
// contaría como métodos del `@Repository` sin `@AuthScope`.

// Posiciones de los parámetros de DELETE_MISSING_SQL; el tercero se fija a mano porque un array de SQL necesita
// `setArray` y la conexión para construirlo.
private const val CLUB_PARAM = 1
private const val STUDENT_PARAM = 2
private const val KEEP_PARAM = 3

private const val FIND_SQL =
    "SELECT tag_value_id FROM club_taxonomia.alumno_tag WHERE club_id = ? AND alumno_id = ?"

/**
 * Con el conjunto vacío, `= ANY('{}')` es falso y `NOT falso` es cierto, así que borra todas las asignaciones — que es
 * justo lo que significa reemplazar por una lista vacía.
 */
private const val DELETE_MISSING_SQL =
    "DELETE FROM club_taxonomia.alumno_tag WHERE club_id = ? AND alumno_id = ? AND NOT (tag_value_id = ANY (?))"

/**
 * `DO NOTHING` sobre la clave primaria hace la inserción idempotente: reasignar un valor que el alumno ya tenía no
 * falla ni reescribe su `creado_en`.
 */
private const val INSERT_SQL =
    "INSERT INTO club_taxonomia.alumno_tag (club_id, alumno_id, tag_value_id) VALUES (?, ?, ?) " +
        "ON CONFLICT (alumno_id, tag_value_id) DO NOTHING"

private const val DELETE_ONE_SQL =
    "DELETE FROM club_taxonomia.alumno_tag WHERE club_id = ? AND alumno_id = ? AND tag_value_id = ?"
