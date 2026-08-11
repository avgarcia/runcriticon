package com.runcriticon.clubtaxonomia.application.ports.outbound.persistence

import com.runcriticon.clubtaxonomia.domain.person.StudentSummary
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.tenancy.ClubId

/**
 * Lectura de los alumnos del club para pantallas de listado, con su clasificación.
 *
 * Puerto aparte de [PersonProjection] a propósito, mismo motivo que ya separa [StudentLookup]/[PersonErasure] de
 * ella: aquella es el puerto de **escritura** de la proyección, sus métodos corren en listeners sin sesión; este corre
 * dentro de una petición con principal y se somete al filtro de club.
 */
interface StudentDirectory {
    /**
     * Alumnos del club, con **todos** sus tags, filtrados por [requiredTagValueIds] en AND (mismo criterio que el
     * filtro de un grupo, ADR-0002 D3).
     *
     * [requiredTagValueIds] **vacío devuelve todos los alumnos del club**, no ninguno: a diferencia de la
     * previsualización de un grupo -que simula un filtro aún sin guardar-, el estado base de un listado es la lista
     * completa. Un id que no existe en la taxonomía o que es de otro club simplemente no encaja con nadie y da lista
     * vacía, sin error: es un filtro de lectura, no una validación de escritura.
     */
    fun listByClub(
        clubId: ClubId,
        requiredTagValueIds: Set<TagValueId>,
    ): List<StudentSummary>
}
