package com.runcriticon.identidad.infrastructure.persistence.repositories

import com.runcriticon.identidad.application.ports.outbound.persistence.ClubRepository
import com.runcriticon.identidad.domain.club.Club
import com.runcriticon.identidad.infrastructure.persistence.mappers.ClubMapper
import com.runcriticon.identidad.infrastructure.persistence.mappers.ClubMapperImpl
import com.runcriticon.shared.autorizacion.annotations.AuthScope
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.autorizacion.annotations.Scope
import com.runcriticon.shared.tenancy.ClubId
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Adaptador del puerto [ClubRepository] sobre Spring Data. Es el `@Repository` que ve la malla anti-IDOR: cada método
 * público declara su ámbito (@AuthScope) o lo exime (@NoAuthScope).
 */
@Repository
class ClubRepositoryImpl(
    private val jpa: ClubEntityRepository,
) : ClubRepository {
    private val mapper: ClubMapper = ClubMapperImpl

    @AuthScope(Scope.CLUB)
    override fun findById(clubId: ClubId): Club? = jpa.findById(clubId.value).orElse(null)?.let(mapper::toDomain)

    @NoAuthScope(
        "el club se guarda siempre con el id del principal (actor.clubId), fijado en UpdateClubCommand; " +
            "no hay id de cliente distinto que verificar aquí",
    )
    override fun save(club: Club) {
        jpa.save(mapper.toEntity(club, Instant.now()))
    }
}
