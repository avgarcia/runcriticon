package com.runcriticon.identidad.infrastructure.security

import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.domain.user.UserStatus
import com.runcriticon.shared.autorizacion.AccountActivePort
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Adaptador de [AccountActivePort] sobre la proyección de usuarios de identidad. Traduce la consulta de estado del
 * gate-check a una lectura ligera del campo `estado` ([UserRepository.findStatus]). Vive en
 * `identidad.infrastructure.security` porque es el módulo que posee el estado de la cuenta; el filtro que lo consume
 * vive en el núcleo neutro (`shared.autorizacion.spring`) y no depende de `identidad`.
 *
 * Pasa por el puerto, no por el repositorio de Spring Data, para que la lectura quede bajo la malla de autorización
 * como cualquier otra (ADR-0009 D11): el filtro ya tiene el principal en el contexto, así que el aspecto verifica el
 * club igual que en una petición normal.
 *
 * Fail-closed: un id inexistente, de otro club o con un estado distinto de `ACTIVO` devuelve `false` — el filtro
 * rechaza la petición.
 */
@Component
class AccountActiveAdapter(
    private val userRepository: UserRepository,
) : AccountActivePort {
    override fun isActive(
        clubId: UUID,
        userId: UUID,
    ): Boolean = userRepository.findStatus(ClubId.of(clubId), UserId.of(userId)) == UserStatus.ACTIVO
}
