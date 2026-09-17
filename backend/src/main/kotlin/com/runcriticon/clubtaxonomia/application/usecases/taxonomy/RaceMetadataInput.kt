package com.runcriticon.clubtaxonomia.application.usecases.taxonomy

import arrow.core.raise.Raise
import arrow.core.raise.ensureNotNull
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.tag.Distance
import com.runcriticon.clubtaxonomia.domain.tag.TagValueMetadata
import java.time.LocalDate

/**
 * Entrada plana de metadata de carrera para los comandos de escritura (`AddTagValueCommand`,
 * `ChangeTagValueMetadataCommand`). `null` (el parámetro que la envuelve) es metadata vacía; una vez
 * decidido que hay carrera, [date] y [distance] son obligatorios — [toMetadata] los valida.
 *
 * Existe porque el mapper REST es puro (dominio↔contrato, sin construir errores de dominio): el DTO
 * plano de entrada se desmonta en estos campos y es el caso de uso quien valida su forma.
 */
data class RaceMetadataInput(
    val date: LocalDate?,
    val distance: String?,
)

/** `null` ⇒ [TagValueMetadata.Empty]; si no, valida que [RaceMetadataInput.date] y `.distance` estén presentes. */
internal fun Raise<ClubTaxonomiaError>.toMetadata(input: RaceMetadataInput?): TagValueMetadata {
    if (input == null) return TagValueMetadata.Empty
    val date = ensureNotNull(input.date) { ClubTaxonomiaError.InvalidInput("fecha", "required") }
    val distanceCode = ensureNotNull(input.distance) { ClubTaxonomiaError.InvalidInput("distancia", "required") }
    val distance =
        ensureNotNull(Distance.fromCode(distanceCode)) { ClubTaxonomiaError.InvalidInput("distancia", "invalid") }
    return TagValueMetadata.Race(date, distance)
}
