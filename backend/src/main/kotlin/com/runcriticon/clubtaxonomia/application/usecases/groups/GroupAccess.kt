package com.runcriticon.clubtaxonomia.application.usecases.groups

import arrow.core.raise.Raise
import arrow.core.raise.ensure
import com.runcriticon.clubtaxonomia.application.ports.outbound.persistence.GroupRepository
import com.runcriticon.clubtaxonomia.domain.errors.ClubTaxonomiaError
import com.runcriticon.clubtaxonomia.domain.group.GroupId
import com.runcriticon.shared.tenancy.ClubId

/**
 * Comprueba que el grupo existe **en el club del actor** antes de tocarlo. Es la comprobación a nivel de objeto que
 * cierra el IDOR: la matriz solo dice que este rol puede ajustar grupos, no que pueda ajustar *este* grupo.
 *
 * Vive en `application` y no en el SQL a propósito: la sentencia de escritura repite la guardia por defensa en
 * profundidad, pero quien decide que la respuesta es un 404 —y no un no-op silencioso— es el caso de uso.
 *
 * Es una función y no una clase base por el mismo motivo que [ensureAssignableFilter]: el guardado de autorización
 * tiene que quedar en el bytecode de cada caso de uso.
 */
internal fun Raise<ClubTaxonomiaError>.ensureGroupOfClub(
    groups: GroupRepository,
    clubId: ClubId,
    groupId: GroupId,
) {
    ensure(groups.exists(clubId, groupId)) { ClubTaxonomiaError.GroupNotFound }
}
