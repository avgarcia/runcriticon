package com.runcriticon.clubtaxonomia.domain.taxonomy

/**
 * Resultado de una mutación de [Taxonomy]: la taxonomía completa ya mutada ([taxonomy]) más la entidad concreta
 * creada o cambiada ([changed], con su id ya generado).
 *
 * Se devuelven ambas porque el caso de uso (LAL-80) necesita persistir la fila afectada y responder al cliente con su
 * id (p. ej. `201 Created` + `Location`) sin tener que deducir qué cambió comparando contra la versión anterior. Es el
 * mismo patrón de `Invitation.reissue`, con un tipo nombrado por legibilidad (hay ~8 métodos con esta forma).
 */
data class TaxonomyUpdate<out T>(
    val taxonomy: Taxonomy,
    val changed: T,
)
