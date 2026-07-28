package com.runcriticon.clubtaxonomia.infrastructure.persistence.entities

import com.runcriticon.shared.rgpd.Category
import com.runcriticon.shared.rgpd.RgpdCategory
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Mapeo JPA de un eje de la taxonomía. Un eje (`nivel`, `objetivo`, `terreno`…) no es dato de persona física:
 * categoría RGPD `SIN_PII`.
 *
 * No declara asociación JPA con sus valores: [TagValueEntity] guarda el `tag_key_id` como columna plana. La
 * composición del agregado la reconstruye el mapeador, no Hibernate — así el adaptador controla el orden exacto de
 * las sentencias frente al índice único parcial del nombre, que una cascada de Hibernate no dejaría gobernar.
 */
@Entity
@Table(name = "tag_key", schema = "club_taxonomia")
@RgpdCategory(Category.SIN_PII)
class TagKeyEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID,
    @Column(name = "club_id", nullable = false)
    var clubId: UUID,
    @Column(name = "nombre", nullable = false)
    var name: String,
    @Column(name = "archivado_en")
    var archivedAt: Instant?,
    // No actualizable: en un re-save la columna se ignora y conserva el instante del alta.
    @Column(name = "creado_en", nullable = false, updatable = false)
    var createdAt: Instant,
)
