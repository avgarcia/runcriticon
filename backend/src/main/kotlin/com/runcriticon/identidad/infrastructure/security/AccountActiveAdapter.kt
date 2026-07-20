package com.runcriticon.identidad.infrastructure.security

import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.identidad.infrastructure.persistence.repositories.UserEntityRepository
import com.runcriticon.shared.autorizacion.AccountActivePort
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Adaptador de [AccountActivePort] sobre la proyección de usuarios de identidad. Traduce la consulta de estado del
 * gate-check a una lectura ligera del campo `estado` por id ([UserEntityRepository.findStatusById]). Vive en
 * `identidad.infrastructure.security` porque es el módulo que posee el estado de la cuenta; el filtro que lo consume
 * vive en el núcleo neutro (`shared.autorizacion.spring`) y no depende de `identidad`.
 *
 * Fail-closed: un id inexistente o un estado distinto de `ACTIVO` devuelve `false` — el filtro rechaza la petición.
 */
@Component
class AccountActiveAdapter(
    private val userEntityRepository: UserEntityRepository,
) : AccountActivePort {
    override fun isActive(userId: UUID): Boolean = userEntityRepository.findStatusById(userId) == UserStatus.ACTIVO.name
}
