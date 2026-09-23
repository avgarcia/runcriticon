package com.runcriticon.clubtaxonomia.application.ports.outbound.persistence

import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestion
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestionOverview
import com.runcriticon.clubtaxonomia.domain.group.MergeSuggestionType
import com.runcriticon.shared.tenancy.ClubId

/**
 * Persistencia de las sugerencias de fusión de grupos: el recálculo (vía [MergeSuggestion]) y el
 * descarte del admin/entrenador sobre una sugerencia ya calculada.
 */
interface MergeSuggestionRepository {
    /**
     * Guarda o actualiza la sugerencia: si ya existía una fila con la misma clave (`clubId`, `groupIdA`,
     * `groupIdB`, `type`), solo refresca `calculatedAt` -- **nunca** toca un descarte ya registrado. Es lo que
     * hace que "descartar" sea indefinido mientras la condición siga vigente (AC3): un recálculo posterior que
     * confirma la misma sugerencia no la resucita.
     */
    fun upsert(
        clubId: ClubId,
        suggestion: MergeSuggestion,
    )

    /**
     * Borra la sugerencia si existe -- se llama cuando el recálculo confirma que la condición ya no se cumple
     * (el grupo dejó de ser micro, o el solape cayó por debajo del umbral). Sin efecto si no había fila, incluida
     * una ya descartada: en ambos casos el estado final es "no hay sugerencia", así que no es un error.
     */
    fun delete(
        clubId: ClubId,
        groupIdA: GroupId,
        groupIdB: GroupId,
        type: MergeSuggestionType,
    )

    /**
     * Sugerencias activas (sin descartar) del club, con el nombre de cada grupo, para pintar la pantalla del
     * admin/entrenador. Lista vacía si no hay ninguna -- no es un error.
     */
    fun listActive(clubId: ClubId): List<MergeSuggestionOverview>

    /**
     * Marca como descartada la sugerencia de esa clave exacta.
     *
     * @return `true` si había una fila activa (sin descartar ya) que se acaba de marcar; `false` si no existía
     * ninguna fila con esa clave, o si ya estaba descartada -- el caso de uso traduce `false` a
     * `MergeSuggestionNotFound`.
     */
    fun dismiss(
        clubId: ClubId,
        groupIdA: GroupId,
        groupIdB: GroupId,
        type: MergeSuggestionType,
    ): Boolean
}
