package com.runcriticon.clubtaxonomia.application.ports.outbound.persistence

import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.group.GroupMembers
import com.runcriticon.clubtaxonomia.domain.group.GroupSummary
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.clubtaxonomia.domain.tag.TagValueId
import com.runcriticon.shared.tenancy.ClubId

/**
 * Persistencia de `Group` y resolución de su membresía (ADR-0002 D3+D4).
 */
interface GroupRepository {
    /**
     * Persiste [group] junto con sus `requiredTagValueIds`.
     *
     * **Solo alta**: inserta sin `ON CONFLICT`. Una segunda llamada con el mismo [Group.id] falla -- todavía no
     * existe la operación de reescribir el filtro de un grupo ya creado.
     */
    fun save(
        clubId: ClubId,
        group: Group,
    )

    /**
     * Alumnos que pertenecen efectivamente al grupo ahora mismo: cumplen todos sus `requiredTagValueIds` (D3),
     * ajustado por las excepciones manuales de `grupo_alumno_override` (D4). Sin caché: cada llamada refleja el
     * estado actual de `alumno_tag` y de los overrides.
     *
     * Devuelve conjunto vacío si [groupId] no existe o no pertenece a [clubId] -- no lanza error de dominio.
     */
    fun resolveMembers(
        clubId: ClubId,
        groupId: GroupId,
    ): Set<PersonId>

    /**
     * Alumnos que cumplirían un filtro de tags **que todavía no se ha guardado**: los que tienen todos los
     * [requiredTagValueIds].
     *
     * Es la variante sin grupo de [resolveMembers]: el filtro llega por parámetro en vez de leerse de
     * `grupo_tag_requerido`, y no interviene ninguna excepción manual, porque no hay grupo del que colgarlas. Un
     * filtro vacío devuelve vacío, igual que un grupo guardado sin tags requeridos.
     *
     * Devuelve el nombre además del id porque el constructor de grupos pinta la lista, no solo el contador.
     */
    fun previewMembers(
        clubId: ClubId,
        requiredTagValueIds: Set<TagValueId>,
    ): GroupMembers

    /**
     * Todos los grupos del club con cuánta gente cae dentro de cada uno ahora mismo, ordenados por nombre.
     *
     * El recuento resuelve la membresía de **todos** los grupos en una sola consulta -- no una por grupo -- e incluye
     * las excepciones manuales, descartando a quien no sea un alumno vivo del club. Un grupo cuyo filtro no encaja con
     * nadie sale con cero, no desaparece de la lista: ese es justo el estado que la pantalla señala.
     *
     * Un club sin grupos devuelve lista vacía, no error.
     */
    fun listSummaries(clubId: ClubId): List<GroupSummary>
}
