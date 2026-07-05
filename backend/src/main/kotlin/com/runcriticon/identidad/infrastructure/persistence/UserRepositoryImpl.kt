package com.runcriticon.identidad.infrastructure.persistence

import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.domain.user.Email
import com.runcriticon.identidad.domain.user.User
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.autorizacion.model.ClubId
import com.runcriticon.shared.autorizacion.model.Role
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Adaptador del puerto [UserRepository] sobre Spring Data. Es el `@Repository` que ve la
 * malla anti-IDOR: cada método público declara su ámbito (@AuthScope) o lo exime (@NoAuthScope).
 */
@Repository
class UserRepositoryImpl(
    private val jpa: UserEntityRepository,
) : UserRepository {
    @NoAuthScope("Login: aún no hay principal en el contexto; se busca por email para autenticar (ADR-0003 D5)")
    override fun findByEmail(
        clubId: ClubId,
        email: Email,
    ): User? = jpa.findByClubIdAndNormalizedEmail(clubId.value, email.value)?.toDomain()

    @AuthScope(Scope.CLUB)
    override fun findById(
        clubId: ClubId,
        userId: UserId,
    ): User? = jpa.findByClubIdAndId(clubId.value, userId.value)?.toDomain()

    @NoAuthScope("activación anónima (LAL-9): sin sesión; la autorización la aporta el token de invitación")
    override fun findByIdUnscoped(
        clubId: ClubId,
        userId: UserId,
    ): User? = jpa.findByClubIdAndId(clubId.value, userId.value)?.toDomain()

    @AuthScope(Scope.CLUB)
    override fun listByClubAndRole(
        clubId: ClubId,
        role: Role,
    ): List<User> = jpa.findByClubIdAndRoleOrderByNameAsc(clubId.value, role.name).map { it.toDomain() }

    @NoAuthScope("alta por invitación; club fijado por InviteCoach, rol ADMIN verificado en el caso de uso")
    override fun save(user: User) {
        jpa.save(user.toEntity(Instant.now()))
    }
}
