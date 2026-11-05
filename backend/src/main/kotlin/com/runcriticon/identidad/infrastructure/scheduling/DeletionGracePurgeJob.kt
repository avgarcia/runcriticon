package com.runcriticon.identidad.infrastructure.scheduling

import com.runcriticon.identidad.application.ports.inbound.DeletionGraceExpired
import com.runcriticon.identidad.application.ports.outbound.persistence.UserRepository
import com.runcriticon.identidad.domain.user.User
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/**
 * Purga de la categoría 1 (ADR-0014 D10, ADR-0017 D9): suprime las cuentas que llevan en `BAJA` más de
 * [User.DELETION_GRACE]. Distinta de [IdentidadRetentionJob], que purga la auditoría con un `DELETE` idempotente.
 *
 * El job no suprime: busca las cuentas vencidas de todos los clubes y publica un [DeletionGraceExpired] por cada una,
 * cada evento en su propia transacción. La supresión la hace `DeletionGraceExpiredListener`, porque los repositorios
 * de la supresión exigen un principal de su club y el job no tiene ninguno. El listener bloquea la fila y vuelve a
 * comprobar la baja, así que dos instancias que ejecuten el job a la vez solo suprimen la cuenta una vez, sin lock
 * distribuido (ADR-0017 D3 y D9).
 *
 * La transacción por evento es obligatoria: `@ApplicationModuleListener` solo recibe eventos publicados dentro de una
 * transacción, y uno publicado fuera se perdería sin error. Que sea una por cuenta hace que un fallo al publicar no
 * arrastre a las demás.
 */
@Component
class DeletionGracePurgeJob(
    private val userRepository: UserRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val transaction: TransactionTemplate,
) {
    @Scheduled(cron = "\${runcriticon.identidad.deletion-grace.cron:0 50 3 * * *}")
    fun purge() {
        val due = userRepository.findExpiredDeletions(cutoff = Instant.now().minus(User.DELETION_GRACE))
        val failed =
            due.count { user ->
                runCatching { transaction.executeWithoutResult { publishFor(user) } }
                    .onFailure { log.error("Retención identidad: no se pudo enviar a suprimir una baja vencida", it) }
                    .isFailure
            }
        if (due.isNotEmpty()) {
            log.info(
                "Retención identidad: {} cuentas con la gracia de su baja vencida enviadas a suprimir, {} fallidas",
                due.size - failed,
                failed,
            )
        }
    }

    private fun publishFor(user: User) {
        eventPublisher.publishEvent(
            DeletionGraceExpired(userId = user.id.value, clubId = user.clubId.value, role = user.role),
        )
    }

    private companion object {
        val log = LoggerFactory.getLogger(DeletionGracePurgeJob::class.java)
    }
}
