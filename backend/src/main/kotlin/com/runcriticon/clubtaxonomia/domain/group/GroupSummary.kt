package com.runcriticon.clubtaxonomia.domain.group

/**
 * Un grupo con cuánta gente cae dentro ahora mismo, que es lo que hace falta para pintarlo en una lista.
 *
 * Por composición y no repitiendo los campos de [Group]: el grupo ya sabe describirse; lo único que añade la consulta
 * del listado es el recuento, que no es un atributo del grupo sino el resultado de resolver su filtro contra los tags
 * y las excepciones manuales del momento.
 */
data class GroupSummary(
    val group: Group,
    val memberCount: Int,
)
