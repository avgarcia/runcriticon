package com.runcriticon.clubtaxonomia.domain.tag

/**
 * Distancia estándar de una carrera (metadata de un `TagValue` del eje `objetivo`).
 *
 * Réplica local del módulo: cada bounded context modela su propia distancia y el contrato que cruza fronteras es solo
 * el [code] persistido (`"5K"/"10K"/"21K"/"42K"`), que viaja en eventos y en el JSONB de `metadata`. Planificación
 * (ritmos relativos) y Seguimiento (marcas) definirán su propia versión cuando la necesiten, con los campos que cada
 * contexto requiera (p. ej. metros para resolver ritmo). Aquí no hacen falta: `Race` solo necesita la identidad de la
 * distancia.
 *
 * Los nombres de constante empiezan por `K` porque un identificador Kotlin no puede empezar por dígito; el valor de
 * negocio persistido es [code] (`"5K"`…), coherente con el glosario y con las columnas de enum de los otros módulos.
 */
enum class Distance(
    val code: String,
) {
    K5("5K"),
    K10("10K"),
    K21("21K"),
    K42("42K"),
    ;

    companion object {
        /** Inversa de [code]; `null` si el código no corresponde a ninguna distancia estándar. */
        fun fromCode(code: String): Distance? = entries.firstOrNull { it.code == code }
    }
}
