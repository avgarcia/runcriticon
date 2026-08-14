package com.runcriticon.clubtaxonomia.application.ports.outbound.persistence

import com.runcriticon.clubtaxonomia.domain.group.Group
import com.runcriticon.clubtaxonomia.domain.group.GroupCoach
import com.runcriticon.clubtaxonomia.domain.group.GroupDetail
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
     * Los grupos de [clubId] cuyo filtro de tags requeridos usa **alguno** de [tagValueIds] — la query inversa que
     * necesita el recálculo de membresía: cuando cambian los tags de un alumno, solo hay que recalcular los grupos
     * cuyo filtro toca al menos uno de los valores que cambiaron (`Δ`), porque si ninguno está en el filtro, la
     * condición `tags(alumno) ⊇ filtro(grupo)` no puede haber cambiado de valor.
     *
     * Conjunto vacío si [tagValueIds] está vacío o ninguno se usa en ningún filtro -- no es un error, es la
     * respuesta correcta cuando el cambio no afecta a ningún grupo.
     */
    fun findGroupIdsByAnyRequiredTagValue(
        clubId: ClubId,
        tagValueIds: Set<TagValueId>,
    ): Set<GroupId>

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

    /**
     * El grupo con su composición actual: sus miembros con el motivo por el que lo son y sus exclusiones manuales.
     *
     * Devuelve `null` si [groupId] no existe **o** no pertenece a [clubId] -- misma semántica sin error que
     * [resolveMembers]; convertirlo en `GroupNotFound` es cosa del caso de uso.
     *
     * Es la lectura que alimenta la pantalla del grupo, así que descarta a quien no sea un alumno del club: una
     * excepción manual sobre un entrenador o sobre alguien sin fila en la proyección no aparece por ningún lado.
     */
    fun findDetail(
        clubId: ClubId,
        groupId: GroupId,
    ): GroupDetail?

    /** `true` solo si en [clubId] hay un grupo con ese id. Es la comprobación de pertenencia previa a escribir. */
    fun exists(
        clubId: ClubId,
        groupId: GroupId,
    ): Boolean

    /**
     * Escribe la excepción manual de [studentId] en [groupId]: `included = true` lo mete aunque no cumpla el filtro,
     * `false` lo saca aunque lo cumpla.
     *
     * Idempotente y sin borrado previo para voltearla: una segunda llamada con el sentido contrario sobrescribe la
     * fila. No escribe nada si [groupId] no es de [clubId].
     */
    fun upsertOverride(
        clubId: ClubId,
        groupId: GroupId,
        studentId: PersonId,
        included: Boolean,
    )

    /**
     * Quita la excepción manual, devolviendo la decisión al filtro de tags.
     *
     * @return cuántas filas se borraron: `0` si no había excepción, que no es un error -- quitar lo que no está deja
     * el mismo estado.
     */
    fun deleteOverride(
        clubId: ClubId,
        groupId: GroupId,
        studentId: PersonId,
    ): Int

    /**
     * Entrenadores asignados a [groupId], ordenados por nombre. Lista vacía si [groupId] no existe o no pertenece a
     * [clubId] -- no lanza error de dominio, misma semántica que [resolveMembers].
     */
    fun findCoaches(
        clubId: ClubId,
        groupId: GroupId,
    ): List<GroupCoach>

    /**
     * Vincula a [coachId] con [groupId].
     *
     * Idempotente: una segunda llamada con el mismo par deja el mismo estado (`ON CONFLICT DO NOTHING`, no hay
     * columna que actualizar -- a diferencia de [upsertOverride], la asignación no tiene sentido). No escribe nada si
     * [groupId] no es de [clubId].
     */
    fun assignCoach(
        clubId: ClubId,
        groupId: GroupId,
        coachId: PersonId,
    )

    /**
     * Desvincula a [coachId] de [groupId].
     *
     * @return cuántas filas se borraron: `0` si no había asignación, que no es un error -- quitar lo que no está
     * deja el mismo estado.
     */
    fun unassignCoach(
        clubId: ClubId,
        groupId: GroupId,
        coachId: PersonId,
    ): Int
}
