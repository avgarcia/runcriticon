package com.runcriticon.identidad.infrastructure.persistencia

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/**
 * Spring Data JPA. No se anota con @Repository (Spring Data lo registra solo); la malla de
 * autorización (@AuthScope/@NoAuthScope) se aplica en el adaptador [UsuarioRepositorioImpl].
 */
interface UsuarioEntityRepository : JpaRepository<UsuarioEntity, UUID> {
    fun findByClubIdAndEmailNormalizado(
        clubId: UUID,
        emailNormalizado: String,
    ): UsuarioEntity?
}
