package com.runcriticon.clubtaxonomia.application.ports.outbound.persistence

import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.person.PersonId
import com.runcriticon.shared.tenancy.ClubId

/**
 * Persistencia de `Group` y resolución de su membresía (ADR-0002 D3+D4).
 */
interface GroupRepository {
    /**
     * Persiste [group] junto con sus `requiredTagValueIds`.
     *
     * **Solo alta**: inserta sin `ON CONFLICT`. Una segunda llamada con el mismo [Group.id] falla -- no hay
     * caso de uso de reescritura del filtro en este ticket (LAL-90); lo trae un ticket futuro (LAL-91/backend).
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
}
