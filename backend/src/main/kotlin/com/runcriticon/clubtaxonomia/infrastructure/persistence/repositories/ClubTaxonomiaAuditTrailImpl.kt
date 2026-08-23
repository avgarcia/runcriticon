package com.runcriticon.clubtaxonomia.infrastructure.persistence.repositories

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.clubtaxonomia.application.ports.outbound.observability.AuditTrail
import com.runcriticon.clubtaxonomia.domain.audit.AuditEntry
import com.runcriticon.clubtaxonomia.infrastructure.persistence.entities.ClubTaxonomiaAuditEventEntity
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Adaptador del puerto [AuditTrail] sobre Spring Data. Persiste el asiento en `club_taxonomia.evento_auditoria` dentro
 * de la transacción del caso de uso que lo invoca. La anonimización va por `JdbcTemplate` directo: un `UPDATE` masivo
 * con `CASE` por columna no encaja bien en un derived-query de Spring Data — mismo criterio que
 * `identidad.AuditTrailImpl`.
 *
 * Sin mapper Konvert: a diferencia del de `identidad`, este asiento tiene dos fuentes (`clubId` explícito + [AuditEntry]
 * del dominio, que no lleva `clubId` porque no todo módulo con auditoría local es club-scoped), y el mapeo 1:1 restante
 * es trivial — no compensa forzarlo a un mapper generado.
 *
 * **Nombre con prefijo `ClubTaxonomia`**: `identidad` ya tiene un `AuditTrailImpl` (`@Repository`, mismo simple name
 * chocaría al registrar el bean); mismo motivo que el resto de clases de este paquete.
 */
@Repository
class ClubTaxonomiaAuditTrailImpl(
    private val jpa: ClubTaxonomiaAuditEventEntityRepository,
    private val jdbc: JdbcTemplate,
) : AuditTrail {
    @AuthScope(Scope.CLUB)
    override fun record(
        clubId: ClubId,
        entry: AuditEntry,
    ) {
        jpa.save(
            ClubTaxonomiaAuditEventEntity(
                id = UuidCreator.getTimeOrderedEpoch(),
                clubId = clubId.value,
                type = entry.type.name,
                actorId = entry.actorId,
                subjectId = entry.subjectId,
                occurredAt = entry.occurredAt,
                metadata = entry.metadata,
            ),
        )
    }

    /**
     * Invocado desde `StudentDeletionListener`, fuera de una petición HTTP y sin principal: un `@AuthScope(Scope.CLUB)`
     * haría fallar cerrado al aspecto en cada entrega y las supresiones acabarían en la DLQ, que es el peor sitio
     * donde puede acabar un derecho de supresión (mismo razonamiento que `PersonErasureJdbc.erase`).
     */
    @NoAuthScope(
        justificacion =
            "Borrado RGPD dirigido por integration events: sin principal en el listener; el sujeto lo identifica el " +
                "evento publicado por identidad, no entrada de usuario.",
    )
    override fun anonymize(personId: UUID): Int = jdbc.update(ANONYMIZE_SQL, personId, personId, personId, personId)

    private companion object {
        // `CASE` por columna, no un `SET ... = NULL` que despoja ambas en cuanto coincide una: un asiento con
        // `actor_id = entrenador_suprimido` y `sujeto_id = alumno` no debe perder el id del alumno, que no ha
        // pedido nada. Sin tocar `metadata`: lleva ids de valores de tag, no de personas.
        const val ANONYMIZE_SQL =
            """
            UPDATE club_taxonomia.evento_auditoria
               SET actor_id  = CASE WHEN actor_id  = ? THEN NULL ELSE actor_id  END,
                   sujeto_id = CASE WHEN sujeto_id = ? THEN NULL ELSE sujeto_id END
             WHERE (actor_id = ? OR sujeto_id = ?)
            """
    }
}
