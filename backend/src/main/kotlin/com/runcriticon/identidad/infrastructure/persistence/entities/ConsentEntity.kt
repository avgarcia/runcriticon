package com.runcriticon.identidad.infrastructure.persistence.entities

import com.runcriticon.shared.rgpd.Category
import com.runcriticon.shared.rgpd.RgpdCategory
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Mapeo JPA de una fila de consentimiento. Contiene la IP y el user-agent completos de quien concedió:
 * categoría RGPD primaria.
 */
@Entity
@Table(name = "consentimiento", schema = "identidad")
@RgpdCategory(Category.PII_PRIMARIA)
class ConsentEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID,
    @Column(name = "usuario_id", nullable = false)
    var userId: UUID,
    @Column(name = "club_id", nullable = false)
    var clubId: UUID,
    @Column(name = "version_texto", nullable = false)
    var textVersion: String,
    @Column(name = "concedido_en", nullable = false)
    var grantedAt: Instant,
    @Column(name = "revocado_en")
    var revokedAt: Instant?,
    // TEXT, no inet: Hibernate escribiría el parámetro como varchar y Postgres no tiene cast implícito
    // varchar→inet (ver el comentario de la migración V202608250001).
    @Column(name = "ip", nullable = false)
    var ip: String,
    @Column(name = "user_agent", nullable = false)
    var userAgent: String,
)
