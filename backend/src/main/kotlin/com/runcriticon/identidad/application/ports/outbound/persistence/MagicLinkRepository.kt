package com.runcriticon.identidad.application.ports.outbound.persistence

import com.runcriticon.identidad.domain.invitation.TokenHash
import com.runcriticon.identidad.domain.magiclink.MagicLink
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.tenancy.ClubId

/**
 * Puerto de persistencia del agregado [MagicLink].
 * La implementación vive en infraestructura; el dominio y los casos de uso solo conocen esta interfaz.
 */
interface MagicLinkRepository {
    fun save(magicLink: MagicLink)

    /** Busca por hash del token presentado (consumo del magic link). Devuelve null si no existe. */
    fun findByTokenHash(tokenHash: TokenHash): MagicLink?

    /** Borra los magic links del usuario al ejercer el derecho de supresión. */
    fun deleteByUserId(
        clubId: ClubId,
        userId: UserId,
    )
}
