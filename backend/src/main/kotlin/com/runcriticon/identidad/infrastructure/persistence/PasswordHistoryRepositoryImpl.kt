package com.runcriticon.identidad.infrastructure.persistence

import com.github.f4b6a3.uuid.UuidCreator
import com.runcriticon.identidad.application.ports.PasswordHistory
import com.runcriticon.identidad.domain.user.UserId
import com.runcriticon.shared.autorizacion.annotations.NoAuthScope
import com.runcriticon.shared.autorizacion.model.ClubId
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Adaptador del puerto [PasswordHistory] sobre Spring Data. Acotado por `userId`, así que no
 * necesita el filtro de club del principal: se usa en flujos sin sesión (activación, reseteo).
 */
@Repository
class PasswordHistoryRepositoryImpl(
    private val jpa: PasswordHistoryEntityRepository,
) : PasswordHistory {
    @NoAuthScope("activación/reseteo sin sesión; histórico acotado por el userId que se está fijando")
    override fun recentHashes(
        userId: UserId,
        count: Int,
    ): List<String> =
        jpa
            .findByUserIdOrderByCreatedAtDesc(userId.value, PageRequest.of(0, count))
            .map { it.passwordHash }

    @NoAuthScope("activación/reseteo sin sesión; registra el hash propio del usuario")
    override fun record(
        userId: UserId,
        clubId: ClubId,
        passwordHash: String,
        now: Instant,
    ) {
        jpa.save(
            PasswordHistoryEntity(UuidCreator.getTimeOrderedEpoch(), userId.value, clubId.value, passwordHash, now),
        )
    }
}
