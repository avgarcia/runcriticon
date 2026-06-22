package com.runcriticon.identidad.infrastructure.persistence

import com.runcriticon.shared.rgpd.Category
import com.runcriticon.shared.rgpd.RgpdCategory
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Mapeo JPA del asiento de auditoría de identidad (ADR-0003 D15). Categoría RGPD AUDITORIA_IDENTIDAD
 * (ADR-0014). Mapea las columnas obligatorias; `ip` (INET) y `metadata` (JSONB) existen en la tabla
 * pero no se mapean hasta que una feature las use (su tipado Postgres llega entonces).
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
)
