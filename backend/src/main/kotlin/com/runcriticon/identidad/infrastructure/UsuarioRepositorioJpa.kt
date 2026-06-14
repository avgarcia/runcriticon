package com.runcriticon.identidad.infrastructure

import com.runcriticon.identidad.application.RepositorioDeUsuarios
import com.runcriticon.identidad.domain.Email
import com.runcriticon.identidad.domain.Usuario
import com.runcriticon.shared.autorizacion.NoAuthScope
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Adaptador del puerto [RepositorioDeUsuarios] sobre Spring Data. Es el `@Repository` que ve la
 * malla anti-IDOR: cada método público declara su ámbito (@AuthScope) o lo exime (@NoAuthScope).
 */
@Repository
class UsuarioRepositorioJpa(
    private val jpa: UsuarioJpaRepository,
) : RepositorioDeUsuarios {
    @NoAuthScope("Login: aún no hay principal en el contexto; se busca por email para autenticar (ADR-0003 D5)")
    override fun buscarPorEmail(
        clubId: UUID,
        email: Email,
    ): Usuario? = jpa.findByClubIdAndEmailNormalizado(clubId, email.valor)?.aDominio()
}
