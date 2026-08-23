package com.runcriticon.clubtaxonomia.infrastructure.persistence.entities

import com.runcriticon.shared.rgpd.Category
import com.runcriticon.shared.rgpd.RgpdCategory
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * Mapeo JPA del asiento de auditoría local de `club_taxonomia`. Categoría RGPD `AUDITORIA_IDENTIDAD` (auditoría local
 * de módulo, ADR-0014 D5) — mismo patrón que `identidad.evento_auditoria`, distinto del bounded context `auditoria`.
 *
 * **Nombre con prefijo `ClubTaxonomia`, no `AuditEventEntity`**: `identidad` ya tiene una entidad JPA con ese simple
 * name para su propia `evento_auditoria`; Hibernate registra los nombres de entidad por simple class name dentro del
 * mismo persistence unit, y dos con el mismo nombre chocan al arrancar el contexto — mismo motivo por el que
 * `auditoria.AuditoriaEventEntity` no se llama `AuditEventEntity`.
 */
@Entity
@Table(name = "evento_auditoria", schema = "club_taxonomia")
@RgpdCategory(Category.AUDITORIA_IDENTIDAD)
class ClubTaxonomiaAuditEventEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID,
    @Column(name = "club_id", nullable = false)
    var clubId: UUID,
    @Column(name = "tipo", nullable = false)
    var type: String,
    @Column(name = "actor_id")
    var actorId: UUID?,
    @Column(name = "sujeto_id")
    var subjectId: UUID?,
    @Column(name = "ts", nullable = false)
    var occurredAt: Instant,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata")
    var metadata: Map<String, List<String>>?,
)
