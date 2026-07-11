package com.runcriticon.identidad.infrastructure.persistence

import com.runcriticon.identidad.application.ports.MagicLinkRepository
import com.runcriticon.identidad.domain.invitation.TokenHash
import com.runcriticon.identidad.domain.magiclink.MagicLink
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import org.springframework.stereotype.Repository

/**
 * Adaptador del puerto [MagicLinkRepository] sobre Spring Data. Es el @Repository que ve la malla
 * anti-IDOR: cada método público declara su ámbito (@AuthScope) o lo exime (@NoAuthScope).
 */
@Repository
class MagicLinkRepositoryImpl(
    private val jpa: MagicLinkEntityRepository,
) : MagicLinkRepository {
    private val mapper: MagicLinkMapper = MagicLinkMapperImpl

    @NoAuthScope("emisión de magic link sin sesión activa; la autoriza el @ApplicationService (LAL-11)")
    override fun save(magicLink: MagicLink) {
        jpa.save(mapper.toEntity(magicLink))
    }

    @NoAuthScope("consumo de magic link: el usuario aún no tiene sesión activa (ADR-0003 D5)")
    override fun findByTokenHash(tokenHash: TokenHash): MagicLink? =
        jpa.findByTokenHash(tokenHash.value)?.let(mapper::toDomain)
}
