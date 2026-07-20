package com.runcriticon.identidad.application.ports.outbound.persistence

import com.runcriticon.identidad.domain.invitation.Invitation
import com.runcriticon.identidad.domain.invitation.TokenHash
import com.runcriticon.identidad.domain.user.UserId

/**
 * Puerto de persistencia del agregado [Invitation].
 * La implementación vive en infraestructura; el dominio y los casos de uso solo conocen esta interfaz.
 */
interface InvitationRepository {
    fun save(invitation: Invitation)

    /** Busca por hash del token presentado (magic link). Devuelve null si no existe. */
    fun findByTokenHash(tokenHash: TokenHash): Invitation?

    /** Devuelve la invitación más reciente del usuario. */
    fun findLatestByUserId(userId: UserId): Invitation?
}
