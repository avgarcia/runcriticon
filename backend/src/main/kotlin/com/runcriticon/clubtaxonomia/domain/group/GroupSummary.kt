package com.runcriticon.clubtaxonomia.domain.group

/**
 * Un grupo con cuánta gente cae dentro ahora mismo y si tiene algún entrenador asignado, que es lo que hace
 * falta para pintarlo en una lista.
 *
 * Por composición y no repitiendo los campos de [Group]: el grupo ya sabe describirse; lo único que añade la consulta
 * del listado es el recuento y el estado de asignación, que no son atributos del grupo sino el resultado de resolver
 * su filtro contra los tags y las excepciones manuales del momento, y de consultar `grupo_entrenador`.
 *
 * [hasCoach] es un booleano y no el entrenador en sí: un grupo puede tener varios (la PK de `grupo_entrenador` es
 * `(grupo_id, entrenador_id)`), y quiénes son se consulta aparte con [GroupCoach].
 */
data class GroupSummary(
    val group: Group,
    val memberCount: Int,
    val hasCoach: Boolean,
)
