package com.runcriticon.clubtaxonomia.infrastructure.rest.mappers

import com.runcriticon.clubtaxonomia.domain.tag.Distance
import com.runcriticon.clubtaxonomia.domain.tag.TagKey
import com.runcriticon.clubtaxonomia.domain.tag.TagValue
import com.runcriticon.clubtaxonomia.domain.tag.TagValueMetadata
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy
import com.runcriticon.shared.api.rest.EmptyMetadata
import com.runcriticon.shared.api.rest.RaceMetadata
import com.runcriticon.shared.api.rest.TagKeyResponse
import com.runcriticon.shared.api.rest.TagValueResponse
import com.runcriticon.shared.api.rest.TaxonomyResponse

/**
 * Traduce el agregado de dominio a los modelos del contrato. Vive aquí y no como funciones privadas de cada
 * controller porque [toResponse] de un eje la necesitan tanto el listado como las mutaciones.
 *
 * Nombre distinto del `TaxonomyMapper` de `persistence/mappers`, que hace un trabajo diferente (dominio ↔ JPA).
 *
 * **El discriminante del contrato y el de la persistencia divergen a propósito**: el JSONB guarda
 * `{"tipo": "Empty"|"Race"}` y el contrato expone `EMPTY`/`RACE` en mayúsculas, coherente con el resto de enums de
 * la spec. No "arreglar" uno por el otro: cambiar el de persistencia exigiría migrar datos.
 */
internal fun Taxonomy.toResponse(): TaxonomyResponse = TaxonomyResponse(tags = keys.map { it.toResponse() })

internal fun TagKey.toResponse(): TagKeyResponse =
    TagKeyResponse(
        id = id.value,
        nombre = label.value,
        valores = values.map { it.toResponse() },
        archivadoEn = archivedAt?.atOffset(java.time.ZoneOffset.UTC),
    )

internal fun TagValue.toResponse(): TagValueResponse =
    TagValueResponse(
        id = id.value,
        valor = label.value,
        metadata = metadata.toResponse(),
        archivadoEn = archivedAt?.atOffset(java.time.ZoneOffset.UTC),
    )

/** `when` exhaustivo sin `else`: añadir una variante de metadata romperá aquí la compilación a propósito. */
private fun TagValueMetadata.toResponse(): com.runcriticon.shared.api.rest.TagValueMetadata =
    when (this) {
        TagValueMetadata.Empty -> EmptyMetadata(tipo = EmptyMetadata.Tipo.EMPTY)
        is TagValueMetadata.Race ->
            RaceMetadata(
                tipo = RaceMetadata.Tipo.RACE,
                fecha = date,
                distancia = distance.toResponse(),
            )
    }

/** El contrato viaja por el código de negocio (`"5K"`), no por el nombre de la constante Kotlin (`K5`). */
private fun Distance.toResponse(): RaceMetadata.Distancia = RaceMetadata.Distancia.entries.first { it.value == code }
