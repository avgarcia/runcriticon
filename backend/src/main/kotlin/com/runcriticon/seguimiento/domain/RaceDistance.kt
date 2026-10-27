package com.runcriticon.seguimiento.domain

/**
 * Distancia de referencia de un ritmo relativo (ADR-0002 D6). Mismo catálogo que
 * `planificacion.domain.RaceDistance`.
 *
 * [meters] son las distancias oficiales (21,0975 km / 42,195 km redondeadas a metros enteros para 21K/42K),
 * no divisiones triviales de 5K — los ritmos resueltos por alumno las usan para convertir el tiempo de una
 * marca a segundos por kilómetro.
 *
 * [literal] es la forma de la distancia en las columnas de este módulo, en los eventos (`MarcaActualizada`,
 * `PlanPublicado`, `PersonalizacionAplicada`… la declaran como `enum` en su JSON Schema) y en la ruta REST de
 * las marcas. Vive aquí, en un único sitio, porque el nombre de la constante no puede serlo (un identificador
 * Kotlin no empieza por dígito). Antes estaba copiado en cada adaptador y listener que lo necesitaba, cada copia
 * con su propia reacción ante un literal desconocido; una de ellas descartaba el evento en silencio.
 */
enum class RaceDistance(
    val meters: Int,
    val literal: String,
) {
    FIVE_K(5_000, "5K"),
    TEN_K(10_000, "10K"),
    HALF_MARATHON(21_097, "21K"),
    MARATHON(42_195, "42K"),
    ;

    companion object {
        /**
         * Para entrada no confiable (el segmento `{distancia}` de la ruta REST): `null` si [literal] no es del
         * catálogo, y el llamador responde con un error de validación.
         */
        fun fromLiteralOrNull(literal: String): RaceDistance? = entries.firstOrNull { it.literal == literal }

        /**
         * Para fuentes de confianza (columnas propias y eventos que fijan el literal en su schema): un valor
         * fuera del catálogo es un contrato roto, no un dato que descartar, así que falla en vez de devolver
         * `null`. En un listener, el fallo deja la publicación sin completar en el outbox (ADR-0007 D13), a la
         * vista, en lugar de darla por procesada.
         */
        fun fromLiteral(literal: String): RaceDistance =
            requireNotNull(fromLiteralOrNull(literal)) { "Literal de distancia desconocido: $literal" }
    }
}
