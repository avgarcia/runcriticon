package com.runcriticon.clubtaxonomia.domain.studenttags

import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.tag.TagKeyId
import com.runcriticon.clubtaxonomia.domain.tag.TagValue
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.clubtaxonomia.domain.taxonomy.Taxonomy

/**
 * Clasificación de un alumno: los valores de la taxonomía que tiene asignados, con el eje del que cuelga cada uno.
 *
 * **No es un agregado**, igual que [com.runcriticon.clubtaxonomia.domain.person.Person]: no impone invariantes ni
 * decide nada. Las tres reglas que podrían parecer suyas viven en otro sitio — que un valor sea asignable lo dice
 * [Taxonomy.assignableValues], que no haya duplicados lo impone la clave primaria de la tabla, y que un alumno pueda
 * tener varios valores del mismo eje es *ausencia* de regla. Es el modelo de lectura que comparten las cuatro
 * operaciones de clasificación, para que la derivación «ids sueltos + taxonomía → lista ordenada» viva en un solo
 * sitio y se pueda probar sin base de datos.
 */
data class StudentTags(
    val studentId: PersonId,
    val assigned: List<AssignedTagValue>,
) {
    companion object {
        /**
         * Compone la clasificación recorriendo la taxonomía en su propio orden —ejes por orden de alta, y dentro de
         * cada eje sus valores— y quedándose con los que estén en [assignedIds]. Recorrer la taxonomía y no los ids
         * es lo que hace que el resultado sea **determinista**: la interfaz pinta los chips siempre igual, sin
         * depender del orden en que se asignaron.
         *
         * Incluye los valores **archivados** que el alumno tenga asignados: archivar deja de ofrecer un valor para
         * asignaciones nuevas, pero no retira las que ya existían.
         *
         * Un id que no esté en la taxonomía se ignora en silencio. Es defensivo: los casos de uso los rechazan antes,
         * así que llegar aquí con uno significaría que la fila sobrevivió al borrado de su valor, y en ese caso es
         * mejor devolver el resto de la clasificación que romper la lectura entera.
         */
        fun of(
            studentId: PersonId,
            taxonomy: Taxonomy,
            assignedIds: Set<TagValueId>,
        ): StudentTags =
            StudentTags(
                studentId = studentId,
                assigned =
                    taxonomy.keys.flatMap { key ->
                        key.values
                            .filter { it.id in assignedIds }
                            .map { AssignedTagValue(keyId = key.id, value = it) }
                    },
            )
    }
}

/** Un valor asignado junto al eje del que cuelga; el eje no se puede deducir del valor por sí solo. */
data class AssignedTagValue(
    val keyId: TagKeyId,
    val value: TagValue,
)
