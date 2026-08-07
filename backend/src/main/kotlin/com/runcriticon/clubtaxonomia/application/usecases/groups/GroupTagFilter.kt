package com.runcriticon.clubtaxonomia.application.usecases.groups

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy

/**
 * Valida el filtro de tags de un grupo contra la taxonomía del club: los valores tienen que existir y ser
 * asignables.
 *
 * La pertenencia al club no necesita comprobación aparte. [taxonomy] es siempre la del club del actor, así que un
 * valor de otro club sencillamente no está en ella y sale como [ClubTaxonomiaError.TagValueNotFound] -- un 404 que
 * no revela que ese id existe en otra parte.
 *
 * Un valor archivado (o cuyo eje lo esté) es un conflicto, no un "no existe": el valor está ahí y quien mira la
 * pantalla lo ve, pero un filtro que se define ahora no puede apoyarse en algo que ya no se asigna. Aquí no cabe la
 * excepción que sí tiene la clasificación, donde lo que el alumno ya llevaba se conserva.
 *
 * Es una función y no una clase base porque el guardado de autorización debe quedar en el bytecode de cada caso de
 * uso: una superclase se lo llevaría fuera.
 */
internal fun Raise<ClubTaxonomiaError>.ensureAssignableFilter(
    taxonomy: Taxonomy,
    valueIds: Set<TagValueId>,
) {
    if (valueIds.isEmpty()) return
    val assignable = taxonomy.assignableValues().mapTo(mutableSetOf()) { it.id }
    valueIds.forEach { valueId ->
        ensure(taxonomy.findValue(valueId) != null) { ClubTaxonomiaError.TagValueNotFound }
        ensure(valueId in assignable) { ClubTaxonomiaError.Conflict("tag_value_not_assignable") }
    }
}
