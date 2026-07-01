package com.runcriticon.identidad.infrastructure.persistence

import com.runcriticon.identidad.domain.invitation.TokenHash
import com.runcriticon.identidad.domain.magiclink.MagicLink
import com.runcriticon.identidad.domain.magiclink.MagicLinkId
import com.runcriticon.identidad.domain.magiclink.MagicLinkPurpose
import com.runcriticon.identidad.domain.user.UserId

/**
 * Mapeo entity <-> dominio (manual en H1 para no depender de la generación de Konvert). El propósito
 * se persiste como string en castellano (ADR-0008 D4) y se traduce al enum de dominio.
 */
internal fun MagicLinkEntity.toDomain(): MagicLink =
    MagicLink(
        id = MagicLinkId.of(id),
        userId = UserId.of(userId),
        clubId = clubId,
        tokenHash = TokenHash(tokenHash),
        proposito = MagicLinkPurpose.valueOf(purpose),
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
        purpose = proposito.name,
        issuedAt = issuedAt,
        expiresAt = expiresAt,
        consumedAt = consumedAt,
    )
