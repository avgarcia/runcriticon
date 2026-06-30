package com.runcriticon.identidad.infrastructure.persistence

import com.runcriticon.identidad.domain.invitation.TokenHash
import com.runcriticon.identidad.domain.magiclink.MagicLink
import com.runcriticon.identidad.domain.magiclink.MagicLinkId
import com.runcriticon.identidad.domain.user.UserId

/**
 * Mapeo entity <-> dominio (manual en H1 para no depender de la generación de Konvert).
 */
internal fun MagicLinkEntity.toDomain(): MagicLink =
    MagicLink(
        id = MagicLinkId.of(id),
        userId = UserId.of(userId),
        clubId = clubId,
        tokenHash = TokenHash(tokenHash),
        issuedAt = issuedAt,
        expiresAt = expiresAt,
        consumedAt = consumedAt,
    )

internal fun MagicLink.toEntity(): MagicLinkEntity =
    MagicLinkEntity(
        id = id.value,
        userId = userId.value,
        clubId = clubId,
        tokenHash = tokenHash.value,
        issuedAt = issuedAt,
        expiresAt = expiresAt,
        consumedAt = consumedAt,
    )
