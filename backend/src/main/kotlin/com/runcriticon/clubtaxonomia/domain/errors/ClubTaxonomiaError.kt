package com.runcriticon.clubtaxonomia.domain.errors

/**
 * Errores del módulo club y taxonomía.
 *
 * Se devuelven como `Either<ClubTaxonomiaError, T>` (Raise DSL); el dominio nunca lanza excepción de negocio.
 * Las variantes van en inglés; los valores de [InvalidInput.field] / [DuplicateLabel.field] usan el vocabulario de
 * negocio en castellano (`"nombre"`, `"valor"`) porque los traduce la capa REST.
 *
 * Variantes previstas que aún no se declaran (se añaden con su historia, para no dejar ramas `when` inalcanzables):
 *  - `TagKeyRequiredByGroup` / `TagValueRequiredByGroup` → cuando el archivado de una etiqueta tenga que bloquearse
 *    porque un grupo vivo la exige en su filtro.
 *  - `ProjectionStale` → cuando se implante la puerta que rechaza leer una proyección local retrasada. Hoy la
 *    clasificación de alumnos no la aplica: un retraso solo produce un `StudentNotFound` reintentable, nunca una
 *    asignación incorrecta.
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
     * No hay en el club un grupo con ese id.
     *
     * Colapsa a propósito que no exista y que exista pero sea de otro club, por el mismo motivo que [StudentNotFound]:
     * distinguirlas dejaría que alguien enumerase los grupos de clubes ajenos comparando respuestas.
     */
    data object GroupNotFound : ClubTaxonomiaError()

    /**
     * No hay en la proyección del club una persona con ese id **y rol alumno**.
     *
     * Colapsa a propósito tres situaciones: que no exista, que exista pero sea entrenador, y que pertenezca a otro
     * club. Distinguirlas dejaría que alguien enumerase usuarios ajenos comparando respuestas.
     */
    data object StudentNotFound : ClubTaxonomiaError()

    /**
     * No hay en la proyección del club una persona con ese id **y rol entrenador**. Simétrico de [StudentNotFound]
     * y por el mismo motivo: colapsa "no existe", "es alumno" y "es de otro club" en una sola respuesta.
     */
    data object CoachNotFound : ClubTaxonomiaError()

    /**
     * La operación choca con el estado actual de la taxonomía: p. ej. añadir un valor a un `TagKey` archivado.
     * [reason] es un código estable en inglés (`"tag_key_archived"`, `"duplicate_id"`), nunca prosa: lo traduce la
     * capa REST.
     */
    data class Conflict(
        val reason: String,
    ) : ClubTaxonomiaError()
}
