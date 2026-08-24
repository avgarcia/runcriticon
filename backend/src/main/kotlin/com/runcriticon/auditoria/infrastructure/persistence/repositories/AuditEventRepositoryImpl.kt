package com.runcriticon.auditoria.infrastructure.persistence.repositories

import com.runcriticon.auditoria.application.ports.outbound.persistence.AuditEventFilter
import com.runcriticon.auditoria.application.ports.outbound.persistence.AuditEventRepository
import com.runcriticon.auditoria.domain.AuditEvent
import com.runcriticon.auditoria.domain.AuditEventId
import com.runcriticon.auditoria.domain.AuditEventType
import com.runcriticon.auditoria.infrastructure.persistence.mappers.AuditEventMapper
import com.runcriticon.auditoria.infrastructure.persistence.mappers.AuditEventMapperImpl
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

/**
 * Adaptador de [AuditEventRepository]. La escritura pasa por Spring Data (`save`, vía [AuditoriaEventEntityRepository]
 * + el mapper Konvert); la consulta forense y la anonimización van por `JdbcTemplate` directo — filtros dinámicos y
 * un `UPDATE` masivo no encajan bien en un derived-query de Spring Data, mismo criterio que el resto de
 * repositorios del repo con lecturas no triviales (`GroupRepositoryJdbc`, `WeeklyPlanRepositoryJdbc`).
 */
@Repository
class AuditEventRepositoryImpl(
    private val jpa: AuditoriaEventEntityRepository,
    private val jdbc: JdbcTemplate,
) : AuditEventRepository {
    private val mapper: AuditEventMapper = AuditEventMapperImpl

    @NoAuthScope(
        justificacion = "Escritura de auditoría del sistema, dentro de la transacción del listener; no hay principal.",
    )
    override fun save(event: AuditEvent) {
        jpa.save(mapper.toEntity(event))
    }

    @AuthScope(Scope.CLUB)
    override fun search(
        clubId: ClubId,
        filter: AuditEventFilter,
    ): List<AuditEvent> {
        val (where, params) = whereClause(clubId, filter)
        val sql =
            """
            SELECT id, club_id, tipo, actor_id, sujeto_id, recurso, motivo, ts
            FROM auditoria.evento
            WHERE $where
            ORDER BY ts DESC
            LIMIT $SEARCH_LIMIT
            """.trimIndent()

        return jdbc.query(sql, ::toAuditEvent, *params.toTypedArray())
    }

    @NoAuthScope(
        justificacion =
            "Invocado desde AuditTrailAnonymizationListener, fuera de una petición HTTP y sin principal; el " +
                "filtro es por personId (actor o sujeto), no por club.",
    )
    override fun anonymize(personId: UUID): Int = jdbc.update(ANONYMIZE_SQL, personId, personId, personId, personId)

    private fun whereClause(
        clubId: ClubId,
        filter: AuditEventFilter,
    ): Pair<String, List<Any>> {
        val where = mutableListOf("club_id = ?")
        val params = mutableListOf<Any>(clubId.value)

        filter.actorId?.let {
            where += "actor_id = ?"
            params += it
        }
        filter.sujetoId?.let {
            where += "sujeto_id = ?"
            params += it
        }
        filter.type?.let {
            where += "tipo = ?"
            params += it.name
        }
        filter.desde?.let {
            where += "ts >= ?"
            params += Timestamp.from(it)
        }
        filter.hasta?.let {
            where += "ts <= ?"
            params += Timestamp.from(it)
        }

        return where.joinToString(" AND ") to params
    }

    private companion object {
        const val SEARCH_LIMIT = 500
    }
}

private fun toAuditEvent(
    rs: ResultSet,
    @Suppress("UNUSED_PARAMETER") rowNum: Int,
): AuditEvent =
    AuditEvent(
        id = AuditEventId.of(rs.getObject("id", UUID::class.java)),
        clubId = ClubId.of(rs.getObject("club_id", UUID::class.java)),
        type = AuditEventType.valueOf(rs.getString("tipo")),
        actorId = rs.getObject("actor_id", UUID::class.java),
        sujetoId = rs.getObject("sujeto_id", UUID::class.java),
        recurso = rs.getString("recurso"),
        motivo = rs.getString("motivo"),
        occurredAt = rs.getTimestamp("ts").toInstant(),
    )

// `CASE` por columna, no un `SET ... = NULL` que despoja ambas en cuanto coincide una: un asiento
// `ACCESO_DENEGADO` con `actor_id = alumno_suprimido` y `sujeto_id = otro_alumno` no debe perder el
// id del otro alumno, que no ha pedido nada. Mismo patrón que `identidad.AuditTrailImpl` (LAL-106) y
// `club_taxonomia.ClubTaxonomiaAuditTrailImpl` (LAL-124).
private const val ANONYMIZE_SQL =
    """
    UPDATE auditoria.evento
       SET actor_id  = CASE WHEN actor_id  = ? THEN NULL ELSE actor_id  END,
           sujeto_id = CASE WHEN sujeto_id = ? THEN NULL ELSE sujeto_id END
     WHERE (actor_id = ? OR sujeto_id = ?)
    """
