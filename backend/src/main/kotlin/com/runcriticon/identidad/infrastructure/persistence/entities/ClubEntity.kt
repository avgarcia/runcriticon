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
 * Mapeo JPA del club. No contiene datos de persona física: categoría RGPD `SIN_PII`. No mapea
 * `zona_horaria` ni `inicio_semana` — columnas latentes con default, sin uso todavía.
 */
@Entity
@Table(name = "club", schema = "identidad")
@RgpdCategory(Category.SIN_PII)
class ClubEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID,
    @Column(name = "nombre", nullable = false)
    var name: String,
    @Column(name = "slug")
    var slug: String?,
    @Column(name = "creado_en", nullable = false, updatable = false)
    var createdAt: Instant,
    @Column(name = "modificado_en", nullable = false)
    var modifiedAt: Instant,
)
