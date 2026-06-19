package com.runcriticon.identidad.infrastructure.persistence

import com.runcriticon.identidad.application.ports.InvitationRepository
import com.runcriticon.identidad.domain.invitation.Invitation
import com.runcriticon.identidad.domain.invitation.TokenHash
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import org.springframework.stereotype.Repository

/**
 * Adaptador del puerto [InvitationRepository] sobre Spring Data. Es el @Repository que ve la
 * malla anti-IDOR: cada método público declara su ámbito (@AuthScope) o lo exime (@NoAuthScope).
 */
@Repository
class InvitationRepositoryImpl(
    private val jpa: InvitationEntityRepository,
) : InvitationRepository {
    @NoAuthScope("flujo de emisión/activación sin sesión activa; la autoriza el @ApplicationService (LAL-7)")
    override fun save(invitation: Invitation) {
        jpa.save(invitation.toEntity())
    }

    @NoAuthScope("verificación de magic link: el usuario aún no tiene sesión activa (ADR-0003 D4)")
    override fun findByTokenHash(tokenHash: TokenHash): Invitation? = jpa.findByTokenHash(tokenHash.value)?.toDomain()

    @NoAuthScope("consulta para reinvitación; el @ApplicationService (LAL-7) comprobará rol ADMIN antes de invocar")
    override fun findLatestByUserId(userId: UserId): Invitation? =
        jpa.findTopByUserIdOrderByIssuedAtDesc(userId.value)?.toDomain()
}
