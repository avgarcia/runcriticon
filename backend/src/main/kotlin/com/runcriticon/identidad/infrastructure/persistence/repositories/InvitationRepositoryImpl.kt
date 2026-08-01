package com.runcriticon.identidad.infrastructure.persistence.repositories

import com.runcriticon.identidad.application.ports.outbound.persistence.InvitationRepository
import com.runcriticon.identidad.domain.invitation.Invitation
import com.runcriticon.identidad.domain.invitation.TokenHash
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.infrastructure.persistence.mappers.InvitationMapper
import com.runcriticon.identidad.infrastructure.persistence.mappers.InvitationMapperImpl
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.stereotype.Repository

/**
 * Adaptador del puerto [InvitationRepository] sobre Spring Data. Es el @Repository que ve la malla anti-IDOR: cada
 * método público declara su ámbito (@AuthScope) o lo exime (@NoAuthScope).
 */
@Repository
class InvitationRepositoryImpl(
    private val jpa: InvitationEntityRepository,
) : InvitationRepository {
    private val mapper: InvitationMapper = InvitationMapperImpl

    @NoAuthScope("flujo de emisión/activación sin sesión activa; la autoriza el @ApplicationService")
    override fun save(invitation: Invitation) {
        jpa.save(mapper.toEntity(invitation))
    }

    @NoAuthScope("verificación de magic link: el usuario aún no tiene sesión activa")
    override fun findByTokenHash(tokenHash: TokenHash): Invitation? =
        jpa.findByTokenHash(tokenHash.value)?.let(mapper::toDomain)

    @AuthScope(Scope.CLUB)
    override fun deleteByUserId(
        clubId: ClubId,
        userId: UserId,
    ) {
        jpa.deleteByClubIdAndUserId(clubId.value, userId.value)
    }

    @NoAuthScope("consulta para reinvitación; el @ApplicationService comprobará rol ADMIN antes de invocar")
    override fun findLatestByUserId(userId: UserId): Invitation? =
        jpa.findTopByUserIdOrderByIssuedAtDesc(userId.value)?.let(mapper::toDomain)
}
