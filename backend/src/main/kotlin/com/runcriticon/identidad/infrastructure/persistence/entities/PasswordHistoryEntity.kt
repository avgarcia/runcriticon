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
 * Mapeo JPA del histórico de contraseñas). Guarda el hash Argon2id vinculado al usuario: categoría RGPD primaria.
 */
@Entity
@Table(name = "password_historico", schema = "identidad")
@RgpdCategory(Category.PII_PRIMARIA)
class PasswordHistoryEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID,
    @Column(name = "usuario_id", nullable = false)
    var userId: UUID,
    @Column(name = "club_id", nullable = false)
    var clubId: UUID,
    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,
    @Column(name = "creado_en", nullable = false)
    var createdAt: Instant,
)
