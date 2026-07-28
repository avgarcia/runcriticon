package com.runcriticon.clubtaxonomia.domain.errors

/**
 * Errores del módulo club y taxonomía.
 *
 * Se devuelven como `Either<ClubTaxonomiaError, T>` (Raise DSL); el dominio nunca lanza excepción de negocio.
 * Las variantes van en inglés; los valores de [InvalidInput.field] / [DuplicateLabel.field] usan el vocabulario de
 * negocio en castellano (`"nombre"`, `"valor"`) porque los traduce la capa REST.
 *
 * Variantes previstas que aún no se declaran (se añaden con su historia, para no dejar ramas `when` inalcanzables):
 *  - `TagKeyRequiredByGroup` / `TagValueRequiredByGroup` → cuando exista el agregado `Grupo`: bloquean el archivado de
 *    una etiqueta requerida por un grupo vivo.
 */
sealed class ClubTaxonomiaError {
    /** El rol del llamador no puede ejecutar la operación sobre la taxonomía (la matriz de autorización lo deniega). */
    data object Forbidden : ClubTaxonomiaError()

    /**
     * Entrada inválida del cliente: nombre en blanco o demasiado largo. [field] y [reason] son estables para que la
     * capa REST los traduzca.
     */
    data class InvalidInput(
        val field: String,
        val reason: String,
    ) : ClubTaxonomiaError()

    /**
     * El nombre ya existe entre los elementos **activos** del mismo ámbito (keys del club, o valores de una misma key),
     * ignorando mayúsculas, acentos y espacios de los extremos. Los archivados no cuentan. [label] es el literal
     * tecleado, para que el editor lo devuelva en el mensaje.
     */
    data class DuplicateLabel(
        val field: String,
        val label: String,
    ) : ClubTaxonomiaError()

    /** No existe un `TagKey` con ese id en la taxonomía del club. */
    data object TagKeyNotFound : ClubTaxonomiaError()

    /** No existe un `TagValue` con ese id en la taxonomía del club. */
    data object TagValueNotFound : ClubTaxonomiaError()

    /**
     * La operación choca con el estado actual de la taxonomía: p. ej. añadir un valor a un `TagKey` archivado.
     * [reason] es un código estable en inglés (`"tag_key_archived"`, `"duplicate_id"`), nunca prosa: lo traduce la
     * capa REST.
     */
    data class Conflict(
        val reason: String,
    ) : ClubTaxonomiaError()
}
