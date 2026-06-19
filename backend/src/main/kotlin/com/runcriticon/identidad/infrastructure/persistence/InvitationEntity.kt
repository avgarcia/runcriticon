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
 * Mapeo JPA de la invitación (ADR-0003 D4, D13). Contiene el token_hash vinculado al usuario:
 * categoría RGPD primaria (ADR-0014). El plugin kotlin-jpa genera el constructor sin argumentos.
 */
@Entity
@Table(name = "invitacion", schema = "identidad")
@RgpdCategory(Category.PII_PRIMARIA)
class InvitationEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID,
    @Column(name = "usuario_id", nullable = false)
    var userId: UUID,
    @Column(name = "club_id", nullable = false)
    var clubId: UUID,
    @Column(name = "token_hash", nullable = false)
    var tokenHash: String,
    @Column(name = "emitida_en", nullable = false)
    var issuedAt: Instant,
    @Column(name = "expira_en", nullable = false)
    var expiresAt: Instant,
    @Column(name = "consumida_en")
    var consumedAt: Instant?,
)
