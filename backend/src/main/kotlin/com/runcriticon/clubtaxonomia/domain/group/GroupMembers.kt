package com.runcriticon.clubtaxonomia.domain.group

import com.runcriticon.clubtaxonomia.domain.person.PersonId

/**
 * Alumno que cumple un filtro de tags, con el nombre que trae la proyección local de personas.
 *
 * No reutiliza `Person` a propósito: esa arrastra email, rol y estado, y aquí solo hace falta identificar al alumno y
 * pintarlo. Cuanto menos dato personal viaje, mejor.
 */
data class GroupMember(
    val id: PersonId,
    val name: String,
)

/**
 * Alumnos que cumplen un filtro de tags, en el orden en que se van a mostrar.
 *
 * [total] es el tamaño de [members], no un total paginado: a la escala prevista —un par de cientos de alumnos por
 * club— la lista no se pagina ni se trunca; de recortarla a los primeros se encarga quien la pinta.
 */
data class GroupMembers(
    val members: List<GroupMember>,
) {
    val total: Int get() = members.size

    companion object {
        val Empty: GroupMembers = GroupMembers(emptyList())
    }
}
