package com.runcriticon.clubtaxonomia.domain.tag

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import java.text.Normalizer
import java.util.Locale

/**
 * Texto de una etiqueta (nombre de `TagKey` o valor de `TagValue`).
 *
 * Guarda el literal tal y como lo tecleó el admin —recortados solo los espacios de los extremos— como forma de
 * visualización (ADR-0002 D2). La comparación de unicidad **no** usa el literal sino [normalized], que replica la
 * expresión `unaccent(lower(trim(x)))` del índice único de PostgreSQL (LAL-79).
 *
 * **Paridad dominio ↔ índice SQL — divergencias conocidas y su dirección:**
 *  1. `unaccent()` de PostgreSQL translitera caracteres que NFD no descompone (`Ø→O`, `Đ→D`, `Ł→L`, `Æ→AE`); aquí
 *     NFD + borrado de marcas combinantes (`\p{Mn}`) los deja intactos. Irrelevante para nombres en castellano, pero
 *     real.
 *  2. `String.trim()` de Kotlin recorta todo whitespace Unicode; `btrim()` de PostgreSQL solo el espacio ASCII. El
 *     dominio es por tanto **más estricto** que la BD: rechaza duplicados que la BD dejaría pasar, nunca al revés —
 *     dirección segura.
 *
 * Por eso el índice único parcial de LAL-79 es la **red de seguridad ante condiciones de carrera**, no la primera
 * línea de defensa: el invariante lo impone el agregado `Taxonomy` con esta normalización.
 */
@JvmInline
value class TagLabel private constructor(
    val value: String,
) {
    /** Forma de comparación de unicidad. Ver la nota de paridad con PostgreSQL en la cabecera de la clase. */
    val normalized: String
        get() =
            Normalizer
                .normalize(value, Normalizer.Form.NFD)
                .replace(DIACRITICS, "")
                .lowercase(Locale.ROOT)

    companion object {
        private val DIACRITICS = Regex("\\p{Mn}+")

        /**
         * Construye una etiqueta validando no-vacío y longitud máxima sobre el literal recortado.
         *
         * @param maxLength límite de caracteres (40 para nombre de key, 60 para valor — wireframe 02).
         * @param field nombre de negocio del campo para el error (`"nombre"`, `"valor"`).
         */
        fun of(
            raw: String,
            maxLength: Int,
            field: String,
        ): Either<ClubTaxonomiaError, TagLabel> =
            either {
                val trimmed = raw.trim()
                ensure(trimmed.isNotEmpty()) { ClubTaxonomiaError.InvalidInput(field, "blank") }
                ensure(trimmed.length <= maxLength) { ClubTaxonomiaError.InvalidInput(field, "too_long") }
                TagLabel(trimmed)
            }
    }
}
