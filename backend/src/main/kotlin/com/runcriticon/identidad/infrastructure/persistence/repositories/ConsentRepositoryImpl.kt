package com.runcriticon.identidad.infrastructure.persistence.repositories

import com.runcriticon.identidad.application.ports.outbound.persistence.ConsentRepository
import com.runcriticon.identidad.domain.consent.Consent
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.identidad.infrastructure.persistence.mappers.ConsentMapper
import com.runcriticon.identidad.infrastructure.persistence.mappers.ConsentMapperImpl
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.stereotype.Repository

/**
 * Adaptador del puerto [ConsentRepository] sobre Spring Data. `save` sirve tanto para conceder (id
 * nuevo, Hibernate hace INSERT) como para revocar (mismo id que la fila vigente, Hibernate hace
 * UPDATE vía `merge`) — ninguna entidad de este módulo usa `@GeneratedValue` ni `Persistable`, así que
 * `save()` resuelve insert-o-update igual que [UserRepositoryImpl][com.runcriticon.identidad.infrastructure.persistence.repositories.UserRepositoryImpl].
 */
@Repository
class ConsentRepositoryImpl(
    private val jpa: ConsentEntityRepository,
) : ConsentRepository {
    private val mapper: ConsentMapper = ConsentMapperImpl

    @NoAuthScope(
        justificacion =
            "Se invoca tanto desde la activación anónima (sin principal, la autoriza el token de " +
                "invitación) como desde GrantConsentCommand/RevokeConsentCommand ya autenticados; el " +
                "clubId siempre sale del usuario resuelto, nunca de un parámetro de entrada del cliente.",
    )
    override fun save(consent: Consent) {
        jpa.save(mapper.toEntity(consent))
    }

    @AuthScope(Scope.CLUB)
    override fun findLatestByUserId(
        clubId: ClubId,
        userId: UserId,
    ): Consent? = jpa.findFirstByClubIdAndUserIdOrderByGrantedAtDesc(clubId.value, userId.value)?.let(mapper::toDomain)

    @AuthScope(Scope.CLUB)
    override fun deleteByUserId(
        clubId: ClubId,
        userId: UserId,
    ): Int = jpa.deleteByClubIdAndUserId(clubId.value, userId.value)
}
