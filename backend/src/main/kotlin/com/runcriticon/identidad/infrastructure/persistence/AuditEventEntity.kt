package com.runcriticon.identidad.infrastructure.persistence

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
 * Mapeo JPA del asiento de auditoría de identidad (ADR-0003 D15). Categoría RGPD AUDITORIA_IDENTIDAD
 * (ADR-0014). La columna `metadata` (JSONB) lleva contexto opcional del evento (p. ej. `email_hash`
 * e `ip` en los eventos de rate-limiting, ADR-0003 D12). La columna `ip` (INET) sigue sin mapearse:
 * la IP viaja dentro de `metadata` para evitar la fricción de binding String↔inet.
 */
@Entity
@Table(name = "evento_auditoria", schema = "identidad")
@RgpdCategory(Category.AUDITORIA_IDENTIDAD)
class AuditEventEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID,
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
    var metadata: Map<String, String>?,
)
