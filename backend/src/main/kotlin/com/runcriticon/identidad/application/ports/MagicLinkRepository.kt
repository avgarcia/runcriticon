package com.runcriticon.identidad.application.ports

import com.runcriticon.identidad.domain.invitation.TokenHash
import com.runcriticon.identidad.domain.magiclink.MagicLink

/**
 * Puerto de persistencia del agregado [MagicLink] (ADR-0003 D5, D13).
 * La implementación vive en infraestructura; el dominio y los casos de uso solo conocen esta interfaz.
 */
interface MagicLinkRepository {
    fun save(magicLink: MagicLink)

    /** Busca por hash del token presentado (consumo del magic link). Devuelve null si no existe. */
    fun findByTokenHash(tokenHash: TokenHash): MagicLink?
}
