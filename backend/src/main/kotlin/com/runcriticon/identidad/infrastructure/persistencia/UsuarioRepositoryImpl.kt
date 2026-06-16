package com.runcriticon.identidad.infrastructure.persistencia

import com.runcriticon.identidad.application.ports.RepositorioDeUsuarios
import com.runcriticon.identidad.domain.usuario.Email
import com.runcriticon.identidad.domain.usuario.Usuario
import com.runcriticon.shared.autorizacion.anotaciones.NoAuthScope
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Adaptador del puerto [RepositorioDeUsuarios] sobre Spring Data. Es el `@Repository` que ve la
 * malla anti-IDOR: cada método público declara su ámbito (@AuthScope) o lo exime (@NoAuthScope).
 */
@Repository
class UsuarioRepositoryImpl(
    private val jpa: UsuarioEntityRepository,
) : RepositorioDeUsuarios {
    @NoAuthScope("Login: aún no hay principal en el contexto; se busca por email para autenticar (ADR-0003 D5)")
    override fun buscarPorEmail(
        clubId: UUID,
        email: Email,
    ): Usuario? = jpa.findByClubIdAndEmailNormalizado(clubId, email.valor)?.toDomain()
}
