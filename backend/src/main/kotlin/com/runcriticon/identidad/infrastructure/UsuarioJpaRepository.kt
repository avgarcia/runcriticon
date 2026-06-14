package com.runcriticon.identidad.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data JPA. No se anota con @Repository (Spring Data lo registra solo); la malla de
 * autorización (@AuthScope/@NoAuthScope) se aplica en el adaptador [UsuarioRepositorioJpa].
 */
interface UsuarioJpaRepository : JpaRepository<UsuarioEntity, UUID> {
    fun findByClubIdAndEmailNormalizado(
        clubId: UUID,
        emailNormalizado: String,
    ): UsuarioEntity?
}
