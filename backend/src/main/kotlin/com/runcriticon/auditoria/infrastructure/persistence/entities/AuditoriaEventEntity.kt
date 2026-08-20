package com.runcriticon.auditoria.infrastructure.persistence.entities

import com.runcriticon.shared.rgpd.Category
import com.runcriticon.shared.rgpd.RgpdCategory
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Mapeo JPA de un asiento de `auditoria.evento`. Categoría RGPD `AUDITORIA_AUTORIZACION`: al ejercer el derecho al
 * olvido, `actor_id`/`sujeto_id` se anonimizan (`AuditTrailAnonymizationListener`), no se borra la fila.
 */
@Entity
@Table(name = "evento", schema = "auditoria")
@RgpdCategory(Category.AUDITORIA_AUTORIZACION)
class AuditoriaEventEntity(
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
    var sujetoId: UUID?,
    @Column(name = "recurso", nullable = false)
    var recurso: String,
    @Column(name = "motivo")
    var motivo: String?,
    @Column(name = "ts", nullable = false)
    var occurredAt: Instant,
)
