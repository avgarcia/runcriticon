package com.runcriticon.clubtaxonomia.domain.group

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError

/**
 * Nombre de un [Group]. Guarda el literal recortado tal y como lo tecleó el admin/entrenador.
 *
 * A diferencia de [com.runcriticon.clubtaxonomia.domain.tag.TagLabel], no normaliza ni exige unicidad: el AC de
 * LAL-90 no pide deduplicar nombres de grupo (dos grupos pueden llamarse igual).
 *
 * `MAX_LENGTH = 80` no está fijado por ningún spec ni por el wireframe `constructor-grupos.html` (sin `maxlength` en
 * el input, solo el ejemplo "Maratón Valencia avanzado"); es un escalado razonado desde
 * [com.runcriticon.clubtaxonomia.domain.tag.TagKey.MAX_LABEL_LENGTH] (40) y
 * [com.runcriticon.clubtaxonomia.domain.tag.TagValue.MAX_LABEL_LENGTH] (60), no una cifra re-derivada de un
 * documento.
 */
@JvmInline
value class GroupName private constructor(
    val value: String,
) {
    companion object {
        const val MAX_LENGTH: Int = 80
        const val FIELD: String = "nombre"

        fun of(raw: String): Either<ClubTaxonomiaError, GroupName> =
            either {
                val trimmed = raw.trim()
                ensure(trimmed.isNotEmpty()) { ClubTaxonomiaError.InvalidInput(FIELD, "blank") }
                ensure(trimmed.length <= MAX_LENGTH) { ClubTaxonomiaError.InvalidInput(FIELD, "too_long") }
                GroupName(trimmed)
            }
    }
}
