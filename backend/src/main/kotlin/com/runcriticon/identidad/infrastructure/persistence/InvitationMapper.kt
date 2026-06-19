package com.runcriticon.identidad.infrastructure.persistence

import com.runcriticon.identidad.domain.invitation.Invitation
import com.runcriticon.identidad.domain.invitation.InvitationId
import com.runcriticon.identidad.domain.invitation.TokenHash
import com.runcriticon.identidad.domain.user.UserId

/**
 * Mapeo entity <-> dominio (manual en H1 para no depender de la generación de Konvert).
 */
internal fun InvitationEntity.toDomain(): Invitation =
    Invitation(
        id = InvitationId.of(id),
        userId = UserId.of(userId),
        clubId = clubId,
        tokenHash = TokenHash(tokenHash),
        issuedAt = issuedAt,
        expiresAt = expiresAt,
        consumedAt = consumedAt,
    )

internal fun Invitation.toEntity(): InvitationEntity =
    InvitationEntity(
        id = id.value,
        userId = userId.value,
        clubId = clubId,
        tokenHash = tokenHash.value,
        issuedAt = issuedAt,
        expiresAt = expiresAt,
        consumedAt = consumedAt,
    )
