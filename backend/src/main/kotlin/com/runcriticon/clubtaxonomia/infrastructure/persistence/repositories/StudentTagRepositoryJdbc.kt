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
import java.sql.ResultSet
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

    @AuthScope(Scope.CLUB)
    override fun countStudentsWithAnyValue(
        clubId: ClubId,
        valueIds: Set<TagValueId>,
    ): Int {
        if (valueIds.isEmpty()) return 0
        val values = valueIds.map { it.value }.toTypedArray()
        return jdbc
            .query(
                COUNT_STUDENTS_SQL,
                { statement: PreparedStatement ->
                    statement.setObject(1, clubId.value)
                    statement.setArray(2, statement.connection.createArrayOf("uuid", values))
                },
                { rs: ResultSet, _: Int -> rs.getInt(1) },
            ).first()
    }

    @AuthScope(Scope.CLUB)
    override fun findAssignedValueIdsByStudent(
        clubId: ClubId,
        studentIds: Set<PersonId>,
    ): Map<PersonId, Set<TagValueId>> {
        if (studentIds.isEmpty()) return emptyMap()
        val ids = studentIds.map { it.value }.toTypedArray()
        val rows =
            jdbc.query(
                FIND_BY_STUDENTS_SQL,
                { statement: PreparedStatement ->
                    statement.setObject(1, clubId.value)
                    statement.setArray(2, statement.connection.createArrayOf("uuid", ids))
                },
                { rs: ResultSet, _: Int ->
                    PersonId.of(rs.getObject(1, UUID::class.java)) to TagValueId.of(rs.getObject(2, UUID::class.java))
                },
            )
        return rows.groupBy({ it.first }, { it.second }).mapValues { (_, values) -> values.toSet() }
    }

    /**
     * Una sola sentencia, no un `batchUpdate` como [replace]: aquí "una sola operación" es también la promesa que
     * hace la historia al usuario, no solo un detalle de implementación. `unnest` expande el array de alumnos para
     * que cada fila del `SELECT` alimente un `INSERT`.
     */
    @AuthScope(Scope.CLUB)
    override fun addToAll(
        clubId: ClubId,
        studentIds: Set<PersonId>,
        valueId: TagValueId,
    ): Int {
        if (studentIds.isEmpty()) return 0
        val ids = studentIds.map { it.value }.toTypedArray()
        return jdbc.update(INSERT_ALL_SQL) { statement: PreparedStatement ->
            statement.setObject(INSERT_ALL_CLUB_PARAM, clubId.value)
            statement.setArray(INSERT_ALL_STUDENTS_PARAM, statement.connection.createArrayOf("uuid", ids))
            statement.setObject(INSERT_ALL_VALUE_PARAM, valueId.value)
        }
    }

    @AuthScope(Scope.CLUB)
    override fun removeFromAll(
        clubId: ClubId,
        studentIds: Set<PersonId>,
        valueId: TagValueId,
    ): Int {
        if (studentIds.isEmpty()) return 0
        val ids = studentIds.map { it.value }.toTypedArray()
        return jdbc.update(DELETE_ALL_SQL) { statement: PreparedStatement ->
            statement.setObject(DELETE_ALL_CLUB_PARAM, clubId.value)
            statement.setObject(DELETE_ALL_VALUE_PARAM, valueId.value)
            statement.setArray(DELETE_ALL_STUDENTS_PARAM, statement.connection.createArrayOf("uuid", ids))
        }
    }
}

// SQL a nivel de fichero: en un `companion object` generaría accesores sintéticos públicos que la malla anti-IDOR
// contaría como métodos del `@Repository` sin `@AuthScope`.

// Posiciones de los parámetros de DELETE_MISSING_SQL; el tercero se fija a mano porque un array de SQL necesita
// `setArray` y la conexión para construirlo.
private const val CLUB_PARAM = 1
private const val STUDENT_PARAM = 2
private const val KEEP_PARAM = 3

// Posiciones de los parámetros de INSERT_ALL_SQL / DELETE_ALL_SQL: cada sentencia ordena club/alumnos/valor de
// forma distinta según qué encaje mejor con el `unnest` o el `ANY`, así que cada una lleva sus propias constantes.
private const val INSERT_ALL_CLUB_PARAM = 1
private const val INSERT_ALL_STUDENTS_PARAM = 2
private const val INSERT_ALL_VALUE_PARAM = 3

private const val DELETE_ALL_CLUB_PARAM = 1
private const val DELETE_ALL_VALUE_PARAM = 2
private const val DELETE_ALL_STUDENTS_PARAM = 3

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

/** Aviso de impacto de archivado: alumnos distintos con alguno de los valores dados. */
private const val COUNT_STUDENTS_SQL =
    "SELECT COUNT(DISTINCT alumno_id) FROM club_taxonomia.alumno_tag WHERE club_id = ? AND tag_value_id = ANY (?)"

private const val FIND_BY_STUDENTS_SQL =
    "SELECT alumno_id, tag_value_id FROM club_taxonomia.alumno_tag WHERE club_id = ? AND alumno_id = ANY (?)"

/**
 * `ON CONFLICT ... DO NOTHING` hace que `jdbc.update` devuelva solo las filas insertadas de verdad: Postgres no
 * cuenta como afectadas las que el conflicto descarta, así que el recuento sale correcto sin postprocesado.
 */
private const val INSERT_ALL_SQL =
    "INSERT INTO club_taxonomia.alumno_tag (club_id, alumno_id, tag_value_id) " +
        "SELECT ?, unnest(?::uuid[]), ? ON CONFLICT (alumno_id, tag_value_id) DO NOTHING"

private const val DELETE_ALL_SQL =
    "DELETE FROM club_taxonomia.alumno_tag WHERE club_id = ? AND tag_value_id = ? AND alumno_id = ANY (?)"
