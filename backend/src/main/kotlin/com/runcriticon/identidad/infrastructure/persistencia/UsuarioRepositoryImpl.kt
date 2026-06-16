package com.runcriticon.identidad.infrastructure.persistencia

import com.runcriticon.identidad.application.ports.UserRepository
import com.runcriticon.identidad.domain.usuario.Email
import com.runcriticon.identidad.domain.usuario.User
import com.runcriticon.shared.autorizacion.anotaciones.NoAuthScope
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Adaptador del puerto [UserRepository] sobre Spring Data. Es el `@Repository` que ve la
 * malla anti-IDOR: cada método público declara su ámbito (@AuthScope) o lo exime (@NoAuthScope).
 */
@Repository
class UsuarioRepositoryImpl(
    private val jpa: UsuarioEntityRepository,
) : UserRepository {
    @NoAuthScope("Login: aún no hay principal en el contexto; se busca por email para autenticar (ADR-0003 D5)")
    override fun findByEmail(
        clubId: UUID,
        email: Email,
    ): User? = jpa.findByClubIdAndNormalizedEmail(clubId, email.value)?.toDomain()
}
