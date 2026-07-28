package com.runcriticon.clubtaxonomia.infrastructure.persistence.entities

import com.runcriticon.clubtaxonomia.domain.tag.TagValueMetadata
import com.runcriticon.clubtaxonomia.infrastructure.persistence.TagValueMetadataJsonbConverter
import com.runcriticon.shared.rgpd.Category
import com.runcriticon.shared.rgpd.RgpdCategory
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * Mapeo JPA de un valor de un eje de la taxonomía. Ni el valor (`5K`, `principiante`…) ni la metadata de una carrera
 * (fecha + distancia) identifican a nadie: categoría RGPD `SIN_PII`. Quien sí es PII es la asignación
 * `alumno_tag`, que no tiene entidad todavía.
 *
 * [metadata] pasa por dos etapas: el [TagValueMetadataJsonbConverter] serializa la `sealed class` de dominio a texto
 * JSON (el discriminante `tipo` lo aporta un mixin, porque el dominio no lleva anotaciones de Jackson) y
 * `@JdbcTypeCode(SqlTypes.JSON)` lo escribe como `jsonb` sin cast manual.
 */
@Entity
@Table(name = "tag_value", schema = "club_taxonomia")
@RgpdCategory(Category.SIN_PII)
class TagValueEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID,
    @Column(name = "tag_key_id", nullable = false)
    var tagKeyId: UUID,
    @Column(name = "club_id", nullable = false)
    var clubId: UUID,
    @Column(name = "nombre", nullable = false)
    var name: String,
    @Convert(converter = TagValueMetadataJsonbConverter::class)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false)
    var metadata: TagValueMetadata,
    @Column(name = "archivado_en")
    var archivedAt: Instant?,
    // No actualizable: en un re-save la columna se ignora y conserva el instante del alta.
    @Column(name = "creado_en", nullable = false, updatable = false)
    var createdAt: Instant,
)
